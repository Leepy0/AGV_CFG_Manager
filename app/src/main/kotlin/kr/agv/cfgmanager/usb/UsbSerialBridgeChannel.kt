package kr.agv.cfgmanager.usb

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.ChannelKind
import kr.agv.cfgmanager.core.bridge.ChannelState
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.bridge.ReplyEnd
import kr.agv.cfgmanager.core.bridge.ReplyRule
import kr.agv.cfgmanager.core.bridge.UsbConsole
import kr.agv.cfgmanager.core.bridge.readReply
import kr.agv.cfgmanager.core.util.Masking
import kr.agv.cfgmanager.session.ConnectFailure
import kr.agv.cfgmanager.session.ConnectStage
import kr.agv.cfgmanager.session.LinkReport
import kr.agv.cfgmanager.session.USB_ADDRESS_PREFIX
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * USB OTG 시리얼 콘솔로 브리지에 붙는 채널 (docs/DEVELOPMENT_PLAN.md 3.3, 8.6).
 * 로그인이 없고, 응답 끝은 `bridge> ` 프롬프트로 판정한다. 주소는 `usb:<장치 이름>`.
 * 115200 8N1, DTR · RTS 는 끈 상태로 연다.
 */
class UsbSerialBridgeChannel(
    private val context: Context,
    private val usb: UsbManager,
    private val prober: UsbSerialProber,
    private val address: String,
    private val onStage: (ConnectStage) -> Unit,
    /** 응답 끝 판정의 유휴 시간. 프롬프트로 끝나므로 보조용이다. */
    private val idleTimeout: Duration = 250.milliseconds,
) : IBridgeChannel, LinkReport {

    override val kind: ChannelKind = ChannelKind.UsbSerial

    private val stateFlow = MutableStateFlow<ChannelState>(ChannelState.Disconnected)
    override val state: StateFlow<ChannelState> = stateFlow.asStateFlow()

    private val rawFlow = MutableSharedFlow<String>(extraBufferCapacity = RAW_BUFFER)
    override val raw: SharedFlow<String> = rawFlow.asSharedFlow()

    private var failureValue: ConnectFailure? = null
    override val failure: ConnectFailure?
        get() = failureValue

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandLock = Mutex()

    @Volatile private var port: UsbSerialPort? = null
    @Volatile private var inbound: Channel<ByteArray> = Channel(Channel.UNLIMITED)
    @Volatile private var reader: Job? = null
    @Volatile private var linked = false

    /** 연결 · 프롬프트 확인. 포트가 이미 열려 있으면(재부팅 뒤) 다시 열지 않고 프롬프트만 기다린다. */
    override suspend fun waitReady(timeout: Duration): Boolean {
        if (linked) return true
        failureValue = null
        val ready = try {
            withTimeoutOrNull(timeout) { connectConsole() } ?: run {
                if (failureValue == null) failureValue = ConnectFailure.Timeout
                false
            }
        } catch (cancelled: CancellationException) {
            closePort()
            stateFlow.value = ChannelState.Disconnected
            throw cancelled
        }
        if (!ready) {
            closePort()
            stateFlow.value = ChannelState.Disconnected
        }
        return ready
    }

    override suspend fun exec(line: String, timeout: Duration): String = commandLock.withLock {
        if (!linked) throw BridgeChannelException.NotConnected()
        sendLine(line, timeout)
    }

    /**
     * `reboot` 은 출력 없이 재시작한다 (docs/DEVELOPMENT_PLAN.md 3.3). 명령이 받아들여졌으니 BLE 와 같은 `rebooting` 으로 맞춘다.
     * 포트는 열어 둔다. 장치가 다시 뜨면 [waitReady] 가 프롬프트를 기다린다.
     */
    override suspend fun execThenDisconnect(line: String): String = commandLock.withLock {
        if (!linked) throw BridgeChannelException.NotConnected()
        val queue = inbound
        drain(queue)
        write(line + "\n")
        val reply = readReply(queue, ReplyRule.IdleOnly, idleTimeout = idleTimeout, maxWait = SHORT_TIMEOUT)
        val body = UsbConsole.replyBody(reply.text, line)
        if (body.isNotEmpty()) rawFlow.tryEmit(body)
        linked = false
        stateFlow.value = ChannelState.Rebooting
        body.ifEmpty { "rebooting" }
    }

    override suspend fun disconnect() {
        closePort()
        stateFlow.value = ChannelState.Disconnected
    }

    // ---- 연결 ----

    private suspend fun connectConsole(): Boolean {
        if (port == null && !openPort()) return false
        return try {
            awaitPrompt()
            linked = true
            stateFlow.value = ChannelState.Connected(address, DEFAULT_NAME, fw = "?")
            true
        } catch (failure: BridgeChannelException) {
            failureValue = ConnectFailure.Other("USB 콘솔이 응답하지 않습니다")
            false
        }
    }

    /** 권한 · 드라이버 · 포트를 열고 줄 설정을 한다. 실패 사유는 [failureValue] 에 넣는다. */
    private suspend fun openPort(): Boolean {
        stateFlow.value = ChannelState.Connecting
        onStage(ConnectStage.Connect)
        val device = usb.deviceList.values.firstOrNull { it.deviceName == address.removePrefix(USB_ADDRESS_PREFIX) }
            ?: run {
                failureValue = ConnectFailure.Other("USB 장치가 연결돼 있지 않습니다")
                return false
            }
        if (!requestUsbPermission(context, usb, device)) {
            failureValue = ConnectFailure.Other("USB 연결을 허용해야 합니다")
            return false
        }
        val driver = prober.probeDevice(device) ?: run {
            failureValue = ConnectFailure.NotBridge
            return false
        }
        val connection = usb.openDevice(device) ?: run {
            failureValue = ConnectFailure.Other("USB 장치를 열지 못했습니다")
            return false
        }
        val opened = driver.ports.first()
        try {
            withContext(Dispatchers.IO) {
                opened.open(connection)
                opened.setParameters(BAUD_RATE, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                opened.setDTR(false)
                opened.setRTS(false)
            }
        } catch (io: IOException) {
            runCatching { opened.close() }
            failureValue = ConnectFailure.Other("USB 포트를 열지 못했습니다")
            return false
        }
        val queue = Channel<ByteArray>(Channel.UNLIMITED)
        inbound = queue
        port = opened
        reader = scope.launch { readLoop(opened, queue) }
        return true
    }

    /** 프롬프트가 올 때까지 빈 줄을 보낸다. 재부팅 중이면 장치가 뜰 때까지 반복한다. 바깥 타임아웃이 끝낸다. */
    private suspend fun awaitPrompt() {
        val queue = inbound
        while (true) {
            drain(queue)
            write("\n")
            val reply = readReply(queue, ReplyRule.Prompt, idleTimeout = idleTimeout, maxWait = PROBE_WAIT)
            if (reply.end == ReplyEnd.Marker) return
        }
    }

    // ---- 명령 송수신 ----

    private suspend fun sendLine(line: String, timeout: Duration): String {
        val queue = inbound
        drain(queue)
        write(line + "\n")
        val reply = readReply(queue, ReplyRule.forCommand(line, kind), idleTimeout = idleTimeout, maxWait = timeout)
        when (reply.end) {
            ReplyEnd.Timeout -> throw BridgeChannelException.Timeout(Masking.redactLine(line))
            ReplyEnd.Closed -> throw BridgeChannelException.Disconnected()
            ReplyEnd.Marker, ReplyEnd.Idle -> Unit
        }
        val body = UsbConsole.replyBody(reply.text, line)
        if (body.isNotEmpty()) rawFlow.tryEmit(body)
        return body
    }

    private suspend fun write(text: String) {
        val p = port ?: throw BridgeChannelException.Disconnected()
        val bytes = text.toByteArray(Charsets.UTF_8)
        withContext(Dispatchers.IO) {
            try {
                p.write(bytes, WRITE_TIMEOUT_MS)
            } catch (io: IOException) {
                throw BridgeChannelException.Disconnected()
            }
        }
    }

    private fun drain(queue: Channel<ByteArray>) {
        while (queue.tryReceive().isSuccess) {
            // 이전 응답의 남은 조각을 버린다
        }
    }

    /** 포트에서 읽어 큐로 보낸다. 읽기 실패(뽑힘)면 끊김으로 알린다. 닫히면 [port] 가 바뀌어 루프가 끝난다. */
    private fun readLoop(p: UsbSerialPort, queue: Channel<ByteArray>) {
        val buffer = ByteArray(READ_BUFFER)
        try {
            while (port === p && p.isOpen) {
                val n = p.read(buffer, READ_POLL_MS)
                if (n > 0) queue.trySend(buffer.copyOf(n))
            }
        } catch (io: IOException) {
            if (port === p) onLost()
        } finally {
            queue.close()
        }
    }

    private fun onLost() {
        linked = false
        stateFlow.value = ChannelState.Disconnected
    }

    private fun closePort() {
        linked = false
        reader?.cancel()
        reader = null
        runCatching { port?.close() }
        port = null
    }

    private companion object {
        const val DEFAULT_NAME = "USB 콘솔"
        const val BAUD_RATE = 115_200
        const val RAW_BUFFER = 256
        const val READ_BUFFER = 1024
        const val READ_POLL_MS = 200
        const val WRITE_TIMEOUT_MS = 2_000
        val SHORT_TIMEOUT: Duration = 3.seconds
        val PROBE_WAIT: Duration = 1.seconds
    }
}
