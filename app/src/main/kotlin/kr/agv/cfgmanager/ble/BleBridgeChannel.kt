package kr.agv.cfgmanager.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.BridgeCommands
import kr.agv.cfgmanager.core.bridge.ChannelKind
import kr.agv.cfgmanager.core.bridge.ChannelState
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.bridge.ReplyEnd
import kr.agv.cfgmanager.core.bridge.ReplyRule
import kr.agv.cfgmanager.core.bridge.ResponseParsers
import kr.agv.cfgmanager.core.bridge.readReply
import kr.agv.cfgmanager.core.util.Masking
import kr.agv.cfgmanager.session.ConnectFailure
import kr.agv.cfgmanager.session.ConnectStage
import kr.agv.cfgmanager.session.LinkReport
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * BLE(NUS) 로 브리지에 붙는 채널 (docs/DEVELOPMENT_PLAN.md 8.3 ~ 8.5).
 * 연결 순서: connectGatt(TRANSPORT_LE) → 200ms → requestMtu(247) → discoverServices → RX/TX → CCCD → 환영 문구 → login.
 * GATT 연산은 [GattQueue] 로 한 번에 하나, 명령은 [commandLock] 으로 한 번에 하나.
 * 비밀번호는 로그에 남기지 않는다(원문 명령은 [Masking.redactLine] 을 거친다).
 */
@SuppressLint("MissingPermission")
class BleBridgeChannel(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val scanner: BleScanner,
    private val address: String,
    private val loginPassword: String,
    private val nameHint: String?,
    private val onStage: (ConnectStage) -> Unit,
) : IBridgeChannel, LinkReport {

    override val kind: ChannelKind = ChannelKind.Ble

    private val stateFlow = MutableStateFlow<ChannelState>(ChannelState.Disconnected)
    override val state: StateFlow<ChannelState> = stateFlow.asStateFlow()

    private val rawFlow = MutableSharedFlow<String>(extraBufferCapacity = RAW_BUFFER)
    override val raw: SharedFlow<String> = rawFlow.asSharedFlow()

    private var failureValue: ConnectFailure? = null
    override val failure: ConnectFailure?
        get() = failureValue

    private val gattQueue = GattQueue()
    private val commandLock = Mutex()
    private val callback = Callback()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var rxChar: BluetoothGattCharacteristic? = null
    @Volatile private var chunkBytes = DEFAULT_MTU - ATT_HEADER
    @Volatile private var linked = false
    @Volatile private var closedByUs = false
    @Volatile private var expectingDisconnect = false
    @Volatile private var inbound: Channel<ByteArray> = Channel(Channel.UNLIMITED)
    @Volatile private var name: String = nameHint ?: address
    @Volatile private var fw: String = "?"

    private sealed interface Attempt {
        data object Ready : Attempt
        data object NotBridge : Attempt
        data class Refused(val status: Int) : Attempt
    }

    override suspend fun waitReady(timeout: Duration): Boolean {
        if (linked) return true
        failureValue = null
        closedByUs = false
        expectingDisconnect = false
        val ready = try {
            withTimeoutOrNull(timeout) { connectAndLogin() } ?: run {
                if (failureValue == null) failureValue = ConnectFailure.Timeout
                false
            }
        } catch (cancelled: CancellationException) {
            closeLink()
            stateFlow.value = ChannelState.Disconnected
            throw cancelled
        }
        if (!ready) {
            closeLink()
            stateFlow.value = ChannelState.Disconnected
        }
        return ready
    }

    override suspend fun exec(line: String, timeout: Duration): String = commandLock.withLock {
        if (!linked) throw BridgeChannelException.NotConnected()
        sendLine(line, timeout)
    }

    override suspend fun execThenDisconnect(line: String): String = commandLock.withLock {
        if (!linked) throw BridgeChannelException.NotConnected()
        val text = sendLine(line, SHORT_TIMEOUT)
        expectingDisconnect = text.trimStart().startsWith("rebooting")
        text
    }

    override suspend fun disconnect() {
        closedByUs = true
        closeLink()
        stateFlow.value = ChannelState.Disconnected
    }

    // ---- 연결 순서 (8.3) ----

    private suspend fun connectAndLogin(): Boolean {
        stateFlow.value = ChannelState.Connecting
        onStage(ConnectStage.Connect)
        var gatt133 = 0
        while (true) {
            when (val attempt = openGatt()) {
                Attempt.Ready -> break
                Attempt.NotBridge -> {
                    failureValue = ConnectFailure.NotBridge
                    return false
                }
                is Attempt.Refused -> {
                    closeLink()
                    if (attempt.status == STATUS_GATT_ERROR) {
                        gatt133++
                        if (gatt133 >= MAX_GATT_133_RETRIES) {
                            failureValue = ConnectFailure.GattUnavailable
                            return false
                        }
                        delay(GATT_133_RETRY_MS.milliseconds)
                    } else {
                        // 광고가 아직 안 보이면 주소로 잠깐 기다린 뒤 다시 연결한다 (재부팅 뒤 재연결)
                        try {
                            scanner.awaitAddress(address, ADVERT_WAIT)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (unavailable: Exception) {
                            delay(ADVERT_WAIT_FALLBACK_MS.milliseconds)
                        }
                    }
                }
            }
        }
        return try {
            afterOpen()
        } catch (failure: BridgeChannelException) {
            failureValue = ConnectFailure.Timeout
            false
        }
    }

    /** 연결 · 서비스 · 구독이 끝난 뒤: 환영 문구 → (필요하면) 로그인. */
    private suspend fun afterOpen(): Boolean {
        val welcome = readReply(inbound, ReplyRule.WELCOME, maxWait = WELCOME_WAIT)
        if (welcome.text.isNotEmpty()) rawFlow.tryEmit(welcome.text)
        val banner = ResponseParsers.parseBanner(welcome.text)
        val needsLogin: Boolean
        if (banner != null) {
            name = banner.name
            fw = banner.fw
            needsLogin = banner.needsLogin
        } else {
            // 환영 문구가 없으면 help 로 대체한다 (구버전 대비).
            val help = sendLine(BridgeCommands.help(), SHORT_TIMEOUT)
            needsLogin = help.contains("login required")
        }
        linked = true
        if (needsLogin) {
            onStage(ConnectStage.Login)
            val reply = sendLine(BridgeCommands.login(loginPassword), SHORT_TIMEOUT)
            when (val result = ResponseParsers.parseLoginResult(reply)) {
                ResponseParsers.LoginResult.Ok -> Unit
                is ResponseParsers.LoginResult.WrongPassword -> {
                    failureValue = if (result.tooManyFailures) {
                        ConnectFailure.TooManyFailures
                    } else {
                        ConnectFailure.WrongPassword(null)
                    }
                    return false
                }
                else -> {
                    failureValue = ConnectFailure.Other("로그인 응답을 해석하지 못했습니다")
                    return false
                }
            }
        }
        stateFlow.value = ChannelState.Connected(address, name, fw)
        return true
    }

    /** connectGatt 부터 CCCD 구독까지. 실패 원인은 [Attempt] 로 돌려준다. */
    private suspend fun openGatt(): Attempt {
        val device: BluetoothDevice = adapter.getRemoteDevice(address)
        inbound = Channel(Channel.UNLIMITED)
        try {
            val opened = gattQueue.run(CONNECT_TIMEOUT) {
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                gatt != null
            }
            if (opened !is GattEvent.Connection || !opened.connected) {
                return Attempt.Refused((opened as? GattEvent.Connection)?.status ?: -1)
            }
            val g = gatt ?: return Attempt.Refused(-1)

            delay(POST_CONNECT_DELAY.milliseconds)
            val mtu = gattQueue.run(MTU_TIMEOUT) { g.requestMtu(REQUESTED_MTU) }
            if (mtu is GattEvent.Mtu && mtu.status == BluetoothGatt.GATT_SUCCESS) {
                chunkBytes = (mtu.mtu - ATT_HEADER).coerceAtLeast(MIN_CHUNK)
            }

            val services = gattQueue.run(SERVICES_TIMEOUT) { g.discoverServices() }
            if (services !is GattEvent.Services || services.status != BluetoothGatt.GATT_SUCCESS) {
                return Attempt.Refused((services as? GattEvent.Services)?.status ?: -1)
            }
            val service = g.getService(NusUuid.SERVICE) ?: return Attempt.NotBridge
            val rx = service.getCharacteristic(NusUuid.RX) ?: return Attempt.NotBridge
            val tx = service.getCharacteristic(NusUuid.TX) ?: return Attempt.NotBridge
            rxChar = rx
            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(NusUuid.CCCD) ?: return Attempt.NotBridge
            val subscribed = gattQueue.run(WRITE_TIMEOUT) { writeCccd(g, cccd) }
            if (subscribed !is GattEvent.Write || subscribed.status != BluetoothGatt.GATT_SUCCESS) {
                return Attempt.Refused((subscribed as? GattEvent.Write)?.status ?: -1)
            }
            return Attempt.Ready
        } catch (failure: GattException) {
            return Attempt.Refused(-1)
        }
    }

    // ---- 명령 송수신 (8.4) ----

    /** 명령 한 줄을 보내고 규칙으로 응답 끝을 판정한다. 잠금은 호출 쪽이 잡는다. */
    private suspend fun sendLine(line: String, timeout: Duration): String {
        val reader = inbound
        while (reader.tryReceive().isSuccess) {
            // 이전 응답의 남은 조각을 버린다
        }
        writeLine(line)
        val reply = readReply(reader, ReplyRule.forCommand(line, kind), maxWait = timeout)
        when (reply.end) {
            ReplyEnd.Timeout -> throw BridgeChannelException.Timeout(Masking.redactLine(line))
            ReplyEnd.Closed -> throw BridgeChannelException.Disconnected()
            ReplyEnd.Marker, ReplyEnd.Idle -> Unit
        }
        if (reply.text.isNotEmpty()) rawFlow.tryEmit(reply.text)
        return reply.text
    }

    /** 줄 + `\n` 을 MTU-3 바이트씩 WRITE_NO_RESPONSE 로 보낸다. 청크마다 콜백을 기다린다. */
    private suspend fun writeLine(line: String) {
        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size - 1 > BridgeCommands.BLE_LINE_MAX_BYTES) {
            throw BridgeChannelException.Disconnected()
        }
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkBytes, bytes.size)
            val part = bytes.copyOfRange(offset, end)
            val event = try {
                gattQueue.run(WRITE_TIMEOUT) { sendChunk(part) }
            } catch (failure: GattException) {
                throw BridgeChannelException.Disconnected()
            }
            if (event !is GattEvent.Write || event.status != BluetoothGatt.GATT_SUCCESS) {
                throw BridgeChannelException.Disconnected()
            }
            offset = end
        }
    }

    @Suppress("DEPRECATION")
    private fun sendChunk(part: ByteArray): Boolean {
        val g = gatt ?: return false
        val rx = rxChar ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(rx, part, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            rx.value = part
            g.writeCharacteristic(rx)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCccd(g: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(descriptor)
        }
    }

    // ---- 끊김 · 정리 (8.5) ----

    /** 가진 GATT 를 끊고 닫는다. 핸들 누수를 막으려고 항상 close 까지 한다. */
    private fun closeLink() {
        linked = false
        inbound.close()
        gattQueue.deliver(GattEvent.Closed)
        val g = gatt ?: return
        gatt = null
        rxChar = null
        try {
            g.disconnect()
        } catch (ignored: SecurityException) {
            // 권한이 없어도 close 는 해야 한다
        }
        try {
            g.close()
        } catch (ignored: SecurityException) {
            // 이미 닫힌 핸들이다
        }
    }

    private fun isStale(g: BluetoothGatt): Boolean {
        val current = gatt
        return current != null && current !== g
    }

    /** 장치가 링크를 끊었다. 재부팅 예정이면 재부팅 대기 상태로 둔다. */
    private fun onLinkLost(g: BluetoothGatt) {
        val wasLinked = linked
        linked = false
        inbound.close()
        gattQueue.deliver(GattEvent.Closed)
        if (gatt === g) gatt = null
        try {
            g.close()
        } catch (ignored: SecurityException) {
            // 이미 닫힌 핸들이다
        }
        if (wasLinked && !closedByUs) {
            stateFlow.value = if (expectingDisconnect) ChannelState.Rebooting else ChannelState.Disconnected
        }
    }

    private inner class Callback : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (isStale(g)) return
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                gattQueue.deliver(GattEvent.Connection(connected = true, status = status))
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gattQueue.deliver(GattEvent.Connection(connected = false, status = status))
                onLinkLost(g)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (!isStale(g)) gattQueue.deliver(GattEvent.Mtu(mtu, status))
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (!isStale(g)) gattQueue.deliver(GattEvent.Services(status))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!isStale(g)) gattQueue.deliver(GattEvent.Write(status))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!isStale(g)) gattQueue.deliver(GattEvent.Write(status))
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (!isStale(g)) inbound.trySend(value)
        }

        /** API 32 이하 경로. API 33 이상에서는 위의 3인자 버전이 불린다. */
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (isStale(g)) return
            characteristic.value?.let { inbound.trySend(it) }
        }
    }

    private companion object {
        const val RAW_BUFFER = 256
        const val DEFAULT_MTU = 23
        const val MIN_CHUNK = 20
        const val ATT_HEADER = 3
        const val REQUESTED_MTU = 247
        const val POST_CONNECT_DELAY = 200L
        const val STATUS_GATT_ERROR = 133
        const val MAX_GATT_133_RETRIES = 3
        const val GATT_133_RETRY_MS = 500L
        const val ADVERT_WAIT_FALLBACK_MS = 1_500L
        val CONNECT_TIMEOUT: Duration = 15.seconds
        val MTU_TIMEOUT: Duration = 5.seconds
        val SERVICES_TIMEOUT: Duration = 10.seconds
        val WRITE_TIMEOUT: Duration = 3.seconds
        val SHORT_TIMEOUT: Duration = 3.seconds
        val WELCOME_WAIT: Duration = 2.seconds
        val ADVERT_WAIT: Duration = 6.seconds
    }
}
