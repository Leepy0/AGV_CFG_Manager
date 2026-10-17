package kr.agv.cfgmanager.core.update

/**
 * 앱 내 업데이트가 쓰는 주소와 버전 규칙 (docs/DEVELOPMENT_PLAN.md 7.5 · 12장).
 * 사내망은 api.github.com 이 막혀 있으므로 이 파일의 주소는 모두 github.com 이다. API 주소는 쓰지 않는다.
 */
object UpdateSpec {
    const val REPO_URL = "https://github.com/Leepy0/AGV_CFG_Manager"

    /** 정식 릴리스의 매니페스트 (12장 형식). 항상 최신 정식 릴리스를 가리킨다. */
    const val MANIFEST_URL = "$REPO_URL/releases/latest/download/AgvCfgManager.json"

    /** 리다이렉트를 따라가지 않고 요청해 `Location` 의 태그로 버전만 얻는 폴백 주소. */
    const val LATEST_PAGE_URL = "$REPO_URL/releases/latest"

    /** 항상 최신 APK 를 받는 브라우저 링크 (README · 정보 화면). */
    const val BROWSER_APK_URL = "$REPO_URL/releases/latest/download/AgvCfgManager.apk"

    const val APK_NAME = "AgvCfgManager.apk"
    const val PARTIAL_NAME = "AgvCfgManager.apk.partial"

    /** 매니페스트 · 확인 요청 제한 시간 (ms). */
    const val CHECK_TIMEOUT_MS = 8_000

    /** 매니페스트가 이 크기를 넘으면 잘못된 응답으로 본다. */
    const val MAX_MANIFEST_BYTES = 64 * 1024

    /** 버전 코드 공식 (12장): X*10000 + Y*100 + Z. */
    fun versionCodeOf(version: UpdateVersion): Int = version.code

    fun apkUrlOf(version: UpdateVersion, file: String = APK_NAME): String =
        "$REPO_URL/releases/download/v$version/$file"

    fun tagPageOf(version: UpdateVersion): String = "$REPO_URL/releases/tag/v$version"
}

/** 채널 규칙 (docs/DEVELOPMENT_PLAN.md 12장). `release` 가 정식이고 dev · local 은 덮어 설치되는 쪽이다. */
object UpdateChannel {
    const val RELEASE = "release"
    const val DEV = "dev"
    const val LOCAL = "local"

    /** dev · local 빌드는 정식 APK 로 덮어 설치된다는 안내를 붙인다. */
    fun isPreview(channel: String): Boolean = channel == DEV || channel == LOCAL
}

/** 버전 `X.Y.Z` (앞의 `v` 허용). Y · Z 는 0~99 여야 버전 코드가 겹치지 않는다. */
data class UpdateVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<UpdateVersion> {

    /** versionCode 로 쓰는 값 (X*10000 + Y*100 + Z). */
    val code: Int get() = major * 10_000 + minor * 100 + patch

    override fun compareTo(other: UpdateVersion): Int = code.compareTo(other.code)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""v?(0|[1-9]\d{0,3})\.(\d{1,2})\.(\d{1,2})""")

        /** 형식이 맞지 않거나 버전 코드가 0 이면 null. */
        fun parse(text: String): UpdateVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            val version = UpdateVersion(major.toInt(), minor.toInt(), patch.toInt())
            return version.takeIf { minor.toInt() <= 99 && patch.toInt() <= 99 && it.code > 0 }
        }
    }
}
