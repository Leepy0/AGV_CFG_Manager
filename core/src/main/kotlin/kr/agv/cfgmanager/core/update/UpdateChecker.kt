package kr.agv.cfgmanager.core.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * 최신 정식 릴리스를 찾는다 (docs/DEVELOPMENT_PLAN.md 7.5).
 * 1순위: 매니페스트 `AgvCfgManager.json`. 실패하면 2순위: `releases/latest` 의 `Location` 태그(`tag/vX.Y.Z`)에서 버전만 얻는다.
 */
class UpdateChecker(
    private val http: UpdateHttp,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Int = UpdateSpec.CHECK_TIMEOUT_MS,
) {
    suspend fun latest(): LatestRelease = withContext(dispatcher) {
        try {
            fromManifest()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (manifestFailure: Exception) {
            try {
                fromTagRedirect()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (fallbackFailure: Exception) {
                throw UpdateFailure("업데이트 정보를 받지 못했습니다. 인터넷 연결을 확인하세요.", fallbackFailure).apply {
                    addSuppressed(manifestFailure)
                }
            }
        }
    }

    private fun fromManifest(): LatestRelease {
        val text = http.get(UpdateSpec.MANIFEST_URL, followRedirects = true, timeoutMs = timeoutMs).use { reply ->
            if (reply.status !in 200..299) {
                throw UpdateFailure("매니페스트 응답 코드 ${reply.status}")
            }
            readLimited(reply.body, UpdateSpec.MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
        }
        return UpdateManifest.parse(text).toRelease()
    }

    private fun fromTagRedirect(): LatestRelease {
        val location = http.get(UpdateSpec.LATEST_PAGE_URL, followRedirects = false, timeoutMs = timeoutMs).use { reply ->
            if (reply.status !in REDIRECT_STATUS) {
                throw UpdateFailure("최신 릴리스 응답 코드 ${reply.status}")
            }
            reply.location ?: throw UpdateFailure("최신 릴리스 주소를 찾지 못했습니다.")
        }
        val version = TAG.find(location)?.groupValues?.get(1)?.let { UpdateVersion.parse(it) }
            ?: throw UpdateFailure("최신 릴리스 태그를 읽지 못했습니다.")
        return LatestRelease(
            version = version,
            versionCode = version.code,
            apkUrl = UpdateSpec.apkUrlOf(version),
            sha256 = null,
            size = null,
            minSdk = null,
            notes = null,
            page = UpdateSpec.tagPageOf(version),
            source = LatestRelease.Source.TagFallback,
        )
    }

    private companion object {
        val REDIRECT_STATUS = 300..399
        val TAG = Regex("""/tag/(v?\d+\.\d+\.\d+)""")
    }
}

/** 스트림을 [limit] 바이트까지만 읽는다. 넘으면 잘못된 응답이다. */
internal fun readLimited(stream: InputStream, limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(4 * 1024)
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        if (out.size() + read > limit) throw UpdateFailure("업데이트 정보가 너무 큽니다.")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
