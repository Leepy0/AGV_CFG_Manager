package kr.agv.cfgmanager.core.util

/** 비밀번호를 로그 · 콘솔 표시에서 가린다. 원문은 저장 · 전송에만 쓴다. */
object Masking {
    const val HIDDEN = "***"

    /** 명령 한 줄에서 비밀번호 인자만 가린다 (`login ...`, `set pass ...`). */
    fun redactLine(line: String): String = when {
        line == "login" || line.startsWith("login ") -> "login $HIDDEN"
        line == "set pass" || line.startsWith("set pass ") -> "set pass $HIDDEN"
        else -> line
    }

    /** 값이 있으면 가림표, 없으면 빈 문자열 (없음은 화면에서 따로 표시). */
    fun hide(secret: String): String = if (secret.isEmpty()) "" else HIDDEN
}
