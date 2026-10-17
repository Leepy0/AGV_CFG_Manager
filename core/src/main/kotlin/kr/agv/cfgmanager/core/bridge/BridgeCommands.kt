package kr.agv.cfgmanager.core.bridge

import kr.agv.cfgmanager.core.util.utf8Length

/**
 * 명령 줄 만들기. BLE 와 USB 콘솔은 값 인용 규칙이 다르다 (docs/DEVELOPMENT_PLAN.md 3.2 · 3.3).
 * - BLE: `set <key> <value>`, 따옴표 없음, 값은 줄 끝까지. 줄은 199바이트까지 (브리지 `CMD_LINE_MAX` 200 - NUL).
 * - USB: argv 토큰. 공백 · `"` · `\` · 빈 값이 있으면 큰따옴표로 감싸고 `"` `\` 를 이스케이프. 160바이트까지.
 */
object BridgeCommands {
    /** 브리지 BLE 입력 줄 한도. 이보다 길면 브리지가 조용히 잘라 값이 깨진다. */
    const val BLE_LINE_MAX_BYTES = 199

    /** 콘솔 명령 줄 한도 (`max_cmdline_length = 160`). 바이트 기준으로 센다. */
    const val CONSOLE_LINE_MAX_BYTES = 160

    sealed interface SetLine {
        data class Ok(val line: String) : SetLine
        data class TooLong(val bytes: Int, val max: Int) : SetLine
        data class Invalid(val reason: String) : SetLine
    }

    fun set(kind: ChannelKind, key: ConfigKey, value: String): SetLine {
        if (value.isEmpty()) return SetLine.Invalid("값이 비어 있습니다")
        if (value.any { it == '\n' || it == '\r' }) return SetLine.Invalid("줄바꿈이 들어 있습니다")
        val (line, max) = when (kind) {
            ChannelKind.UsbSerial -> "set ${key.wire} ${quoteConsoleArg(value)}" to CONSOLE_LINE_MAX_BYTES
            ChannelKind.Ble, ChannelKind.Fake -> "set ${key.wire} $value" to BLE_LINE_MAX_BYTES
        }
        val bytes = line.utf8Length()
        return if (bytes > max) SetLine.TooLong(bytes, max) else SetLine.Ok(line)
    }

    /** argv 토큰으로 안전하게. 특수 문자가 없으면 그대로. */
    fun quoteConsoleArg(value: String): String {
        val needsQuotes = value.isEmpty() || value.any { it == ' ' || it == '\t' || it == '"' || it == '\\' }
        if (!needsQuotes) return value
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    fun cfg(): String = "cfg"

    fun stat(): String = "stat"

    fun save(): String = "save"

    fun reboot(): String = "reboot"

    fun help(): String = "help"

    fun defaults(): String = "defaults"

    /** `login <password>`. 줄 자체에 비밀번호가 들어가므로 로그에는 [kr.agv.cfgmanager.core.util.Masking.redactLine] 을 쓴다. */
    fun login(password: String): String = "login $password"

    /** `setup` (상태만) 또는 `setup on` · `setup off`. */
    fun setup(open: Boolean?): String = when (open) {
        null -> "setup"
        true -> "setup on"
        false -> "setup off"
    }
}
