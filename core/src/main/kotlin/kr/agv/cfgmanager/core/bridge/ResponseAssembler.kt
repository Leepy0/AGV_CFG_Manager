package kr.agv.cfgmanager.core.bridge

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import kr.agv.cfgmanager.core.util.TerminalText
import kr.agv.cfgmanager.core.util.Utf8Stream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 명령별 응답 끝 판정 규칙 (docs/DEVELOPMENT_PLAN.md 3.2.1, 3.3).
 * 브리지 BLE 응답에는 끝 표시가 없으므로 규칙 + 유휴 시간으로 끝을 정한다.
 */
sealed interface ReplyRule {
    /** 첫 줄바꿈이 오면 끝 (set · save · reboot · setup · dsp · help · login). */
    data object SingleLine : ReplyRule

    /** 지정한 접두어의 완결된 줄이 나온 뒤 유휴 시간이 지나면 끝 (cfg: `state=`, stat: `dsp :`, 환영 문구). */
    data class AfterLinePrefix(val prefixes: List<String>) : ReplyRule

    /** 아무 줄도 아직 없으면 계속 기다리고, 내용이 온 뒤 유휴 시간이면 끝 (알 수 없는 명령). */
    data object IdleOnly : ReplyRule

    /** 콘솔(USB): 정리한 텍스트가 `bridge>` 프롬프트로 끝나면 끝. */
    data object Prompt : ReplyRule

    companion object {
        val CFG: ReplyRule = AfterLinePrefix(listOf("state="))
        val STAT: ReplyRule = AfterLinePrefix(listOf("dsp :"))
        val WELCOME: ReplyRule = AfterLinePrefix(listOf("login ", "type help"))

        /** 명령 한 줄과 연결 방식으로 규칙을 고른다. JSON 출력(BR-1)은 한 줄이다. */
        fun forCommand(line: String, kind: ChannelKind): ReplyRule {
            if (kind == ChannelKind.UsbSerial) return Prompt
            val parts = line.trim().split(' ')
            return when (parts.firstOrNull().orEmpty()) {
                "cfg" -> if (parts.getOrNull(1) == "json") SingleLine else CFG
                "stat" -> if (parts.getOrNull(1) == "json") SingleLine else STAT
                "set", "save", "defaults", "reboot", "setup", "dsp", "help", "login" -> SingleLine
                else -> IdleOnly
            }
        }
    }
}

/** 응답이 끝난 이유. */
enum class ReplyEnd { Marker, Idle, Closed, Timeout }

data class Reply(val text: String, val end: ReplyEnd)

/** 청크를 모아 [ReplyRule] 로 끝을 판정하는 순수 상태 기계. 시간은 호출 측이 다룬다. */
class ResponseAssembler(private val rule: ReplyRule) {
    private val buffer = StringBuilder()

    val text: String get() = buffer.toString()

    fun append(chunk: String) {
        buffer.append(chunk)
    }

    /** 규칙의 표지(줄바꿈 · 프롬프트 · 접두어 줄)를 이미 받았는가. */
    fun markerReached(): Boolean = when (val r = rule) {
        ReplyRule.SingleLine -> buffer.indexOf("\n") >= 0
        is ReplyRule.AfterLinePrefix -> completeLines().any { line -> r.prefixes.any { line.startsWith(it) } }
        ReplyRule.IdleOnly -> buffer.isNotEmpty()
        ReplyRule.Prompt -> TerminalText.endsWithPrompt(TerminalText.clean(text))
    }

    /** 표지만으로 바로 끝나는 규칙이고 표지를 받았는가. */
    fun endsOnMarker(): Boolean = (rule == ReplyRule.SingleLine || rule == ReplyRule.Prompt) && markerReached()

    /** 지금 유휴 시간이 지나면 끝나도 되는가. */
    fun mayEndOnIdle(): Boolean = when (rule) {
        ReplyRule.SingleLine, ReplyRule.Prompt -> false
        is ReplyRule.AfterLinePrefix -> markerReached()
        ReplyRule.IdleOnly -> buffer.isNotEmpty()
    }

    private fun completeLines(): List<String> {
        val s = buffer.toString()
        val last = s.lastIndexOf('\n')
        if (last < 0) return emptyList()
        return s.substring(0, last).split('\n').map { it.trimEnd('\r') }
    }
}

/**
 * 채널 수신 큐([chunks])에서 응답 하나를 읽는다. 최대 [maxWait], 유휴 [idleTimeout] (기본 250ms, 3초).
 * 시간은 코루틴 시간을 쓰므로 runTest 의 가상 시간으로 검증할 수 있다.
 */
suspend fun readReply(
    chunks: ReceiveChannel<ByteArray>,
    rule: ReplyRule,
    idleTimeout: Duration = 250.milliseconds,
    maxWait: Duration = 3.seconds,
): Reply {
    val assembler = ResponseAssembler(rule)
    val utf8 = Utf8Stream()
    val end = withTimeoutOrNull(maxWait) {
        collect(chunks, assembler, utf8, idleTimeout)
    }
    return Reply(assembler.text, end ?: ReplyEnd.Timeout)
}

private suspend fun collect(
    chunks: ReceiveChannel<ByteArray>,
    assembler: ResponseAssembler,
    utf8: Utf8Stream,
    idleTimeout: Duration,
): ReplyEnd {
    while (true) {
        if (assembler.endsOnMarker()) return ReplyEnd.Marker
        val received = if (assembler.mayEndOnIdle()) {
            withTimeoutOrNull(idleTimeout) { chunks.receiveCatching() } ?: return ReplyEnd.Idle
        } else {
            chunks.receiveCatching()
        }
        if (received.isClosed) return ReplyEnd.Closed
        assembler.append(utf8.push(received.getOrThrow()))
    }
}
