package kr.agv.cfgmanager.core.profiles

/**
 * 프로필 1개를 QR 에 싣는 문자열 (docs/DEVELOPMENT_PLAN.md F-P5, 부록 B-2).
 * 형식은 내보내기 파일과 같은 JSON 을 한 줄로 쓴 것이다. 라이브러리는 싣지 않는다.
 * 비밀번호는 기본으로 뺀다. QR 한 장에 담을 수 있는 크기를 넘으면 만들지 않는다.
 */
object ProfileQr {

    /** 이 길이(UTF-8 바이트)를 넘으면 QR 을 만들지 않는다. 오류 정정 M 기준으로 휴대폰이 안정적으로 읽는 범위. */
    const val MAX_BYTES = 1_600

    /** QR 문자열을 만든다. 크기를 넘으면 [IllegalArgumentException]. */
    fun encode(profile: Profile, includePasswords: Boolean): String {
        val text = ProfileExport.export(
            ExportBundle(profiles = listOf(profile), networks = emptyList()),
            includePasswords = includePasswords,
            pretty = false,
        )
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "프로필이 너무 커서 QR 로 만들 수 없습니다." }
        return text
    }

    /** 읽은 QR 문자열을 가져오기 묶음으로 바꾼다 (형식이 아니면 [IllegalArgumentException]). */
    fun decode(text: String): ImportedBundle = ProfileExport.import(text)
}
