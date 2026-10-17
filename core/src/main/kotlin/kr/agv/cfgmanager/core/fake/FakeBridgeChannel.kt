package kr.agv.cfgmanager.core.fake

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [FakeDevice] 에 BLE 링크를 입힌 채널. 응답을 [chunkSize] 바이트씩 [chunkDelay] 간격으로 내보내고,
 * 명령마다 응답 끝은 [readReply] 로 판정한다 (하드웨어 없이 워크플로를 검증하는 용도).
 *
 * 시험 주입:
 * - [dropBefore]: 해당 명령을 받기 전에 링크를 끊는다.
 * - [silent]: 해당 명령에 응답하지 않는다(시간 초과).
 */
class FakeBridgeChannel(
    val device: FakeDevice,
    private val loginPassword: String = device.blePassword,
    private val chunkSize: Int = 244,
    private val chunkDelay: Duration = 20.milliseconds,
    private val bootDelay: Duration = 3.seconds,
    private val pollInterval: Duration = 200.milliseconds,
) : IBridgeChannel {

    override val kind: ChannelKind = ChannelKind.Fake

    private val stateFlow = MutableStateFlow<ChannelState>(ChannelState.Disconnected)
    override val state: StateFlow<ChannelState> = stateFlow.asStateFlow()

    private val rawFlow = MutableSharedFlow<String>(extraBufferCapacity = 256)
    override val raw: SharedFlow<String> = rawFlow.asSharedFlow()

    private val gate = Mutex()
    private var linked = false

    var dropBefore: ((String) -> Boolean)? = null
    var silent: ((String) -> Boolean)? = null

    override suspend fun exec(line: String, timeout: Duration): String =
        gate.withLock { command(line, timeout) }

    override suspend fun execThenDisconnect(line: String): String =
        gate.withLock { command(line, 3.seconds) }

    /** 재부팅 뒤 다시 광고할 때까지 기다리고, 연결 · 로그인까지 마친다. */
    override suspend fun waitReady(timeout: Duration): Boolean {
        if (linked) return true
        return withTimeoutOrNull(timeout) {
            connectAndLogin()
        } ?: run {
            stateFlow.value = ChannelState.Disconnected
            false
        }
    }

    override suspend fun disconnect() {
        dropLink()
    }

    private suspend fun connectAndLogin(): Boolean {
        stateFlow.value = ChannelState.Connecting
        while (!device.setupOpen) delay(pollInterval)
        if (device.bootPending) {
            delay(bootDelay)
            device.bootPending = false
        }
        linked = true
        return try {
            deliver(FakeReply(device.welcome()), ReplyRule.WELCOME, 3.seconds)
            if (device.loginRequired) {
                val reply = command(BridgeCommands.login(loginPassword), 3.seconds)
                val ok = ResponseParsers.parseLoginResult(reply) == ResponseParsers.LoginResult.Ok
                if (!ok) {
                    dropLink()
                    return false
                }
            }
            stateFlow.value = ChannelState.Connected(BLE_ADDRESS, device.name, device.fw)
            true
        } catch (failure: BridgeChannelException) {
            dropLink()
            false
        }
    }

    /** 명령 한 번: 주입 확인 → 장치 처리 → 청크 전송 → 끝 판정 → 장치 동작 적용. */
    private suspend fun command(line: String, timeout: Duration): String {
        if (!linked) throw BridgeChannelException.NotConnected()
        val redacted = Masking.redactLine(line)
        if (dropBefore?.invoke(line) == true) {
            dropLink()
            throw BridgeChannelException.Disconnected()
        }
        if (silent?.invoke(line) == true) {
            delay(timeout)
            throw BridgeChannelException.Timeout(redacted)
        }
        val reply = device.handle(line)
        val result = deliver(reply, ReplyRule.forCommand(line, kind), timeout)
        if (result.text.isNotEmpty()) rawFlow.emit(result.text)
        if (result.end == ReplyEnd.Timeout) throw BridgeChannelException.Timeout(redacted)
        if (result.end == ReplyEnd.Closed) {
            dropLink()
            throw BridgeChannelException.Disconnected()
        }
        when (reply.action) {
            FakeAction.None -> Unit
            FakeAction.Disconnect -> dropLink()
            FakeAction.Reboot -> rebootLink()
        }
        return result.text
    }

    /** 응답을 청크로 보내고 [readReply] 로 받는다. CRLF 변환은 BLE 송신과 같다. */
    private suspend fun deliver(reply: FakeReply, rule: ReplyRule, timeout: Duration) = coroutineScope {
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val wire = reply.text.replace("\n", "\r\n").toByteArray(Charsets.UTF_8)
        launch {
            if (reply.delayMs > 0) delay(reply.delayMs.milliseconds)
            var offset = 0
            while (offset < wire.size) {
                val end = minOf(offset + chunkSize, wire.size)
                channel.send(wire.copyOfRange(offset, end))
                offset = end
                if (offset < wire.size) delay(chunkDelay)
            }
            if (reply.action != FakeAction.None) channel.close()
        }
        readReply(channel, rule, maxWait = timeout)
    }

    private fun dropLink() {
        linked = false
        device.onDisconnected()
        stateFlow.value = ChannelState.Disconnected
    }

    private fun rebootLink() {
        linked = false
        device.reboot()
        stateFlow.value = ChannelState.Rebooting
    }

    private companion object {
        /** 부록 A 의 예시 BLE 주소 (공개 주소, 비밀이 아님). */
        const val BLE_ADDRESS = "AA:BB:CC:DD:EE:F8"
    }
}
