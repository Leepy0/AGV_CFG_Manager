package kr.agv.cfgmanager.core.update

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * 릴리스 매니페스트 `AgvCfgManager.json` (docs/DEVELOPMENT_PLAN.md 12장). 모르는 필드는 무시한다.
 * 필드는 CI 가 만든다. 앱은 값을 검사한 뒤 [LatestRelease] 로 바꿔 쓴다.
 */
@Serializable
data class UpdateManifest(
    val version: String,
    val versionCode: Int? = null,
    val file: String = UpdateSpec.APK_NAME,
    val sha256: String? = null,
    val size: Long? = null,
    val minSdk: Int? = null,
    val published: String? = null,
    val page: String? = null,
    val notes: String? = null,
) {
    /** 매니페스트 값을 검사해 릴리스 정보로 바꾼다. 잘못된 값은 [UpdateFailure]. */
    fun toRelease(): LatestRelease {
        val parsed = UpdateVersion.parse(version)
            ?: throw UpdateFailure("업데이트 정보의 버전이 올바르지 않습니다.")
        val code = versionCode ?: parsed.code
        if (code <= 0) throw UpdateFailure("업데이트 정보의 버전 코드가 올바르지 않습니다.")
        if (!APK_FILE.matches(file)) throw UpdateFailure("업데이트 파일 이름이 올바르지 않습니다.")
        val hash = sha256?.let { value ->
            if (!SHA256.matches(value)) throw UpdateFailure("업데이트 파일의 sha256 이 올바르지 않습니다.")
            value.lowercase()
        }
        if (size != null && size <= 0L) throw UpdateFailure("업데이트 파일 크기가 올바르지 않습니다.")
        return LatestRelease(
            version = parsed,
            versionCode = code,
            apkUrl = UpdateSpec.apkUrlOf(parsed, file),
            sha256 = hash,
            size = size,
            minSdk = minSdk,
            notes = notes?.takeIf { it.isNotBlank() },
            page = UpdateSpec.tagPageOf(parsed),
            source = LatestRelease.Source.Manifest,
        )
    }

    companion object {
        private val APK_FILE = Regex("""[A-Za-z0-9._-]+\.apk""")
        private val SHA256 = Regex("""[0-9a-fA-F]{64}""")

        private val json = Json { ignoreUnknownKeys = true }

        /** 매니페스트 본문을 읽는다. JSON 이 아니거나 필수 값이 없으면 [UpdateFailure]. */
        fun parse(text: String): UpdateManifest = try {
            json.decodeFromString(serializer(), text)
        } catch (failure: SerializationException) {
            throw UpdateFailure("업데이트 정보를 읽지 못했습니다.", failure)
        } catch (failure: IllegalArgumentException) {
            throw UpdateFailure("업데이트 정보를 읽지 못했습니다.", failure)
        }
    }
}

/** 최신 정식 릴리스 정보. [source] 가 [Source.TagFallback] 이면 sha256 이 없어 브라우저로만 받는다. */
data class LatestRelease(
    val version: UpdateVersion,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String?,
    val size: Long?,
    val minSdk: Int?,
    val notes: String?,
    val page: String,
    val source: Source,
) {
    enum class Source { Manifest, TagFallback }

    /** 앱 안에서 받아 설치할 수 있는지 (sha256 이 있어야 한다). */
    val installableInApp: Boolean get() = sha256 != null
}

/** 업데이트 확인 · 받기 중 사용자에게 보일 수 있는 실패. 메시지는 한국어 문장이다. */
class UpdateFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
