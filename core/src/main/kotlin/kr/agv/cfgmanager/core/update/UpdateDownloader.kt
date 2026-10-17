package kr.agv.cfgmanager.core.update

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * APK 받기 (docs/DEVELOPMENT_PLAN.md 7.5). `dir/AgvCfgManager.apk.partial` 로 받으며 진행률을 알리고,
 * sha256 (과 크기) 을 맞춘 뒤 `dir/AgvCfgManager.apk` 로 바꾼다. 실패 · 취소하면 부분 파일을 지운다.
 */
class UpdateDownloader(
    private val http: UpdateHttp,
    private val dir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Int = UpdateSpec.CHECK_TIMEOUT_MS,
) {
    /**
     * [release] 를 받아 검증된 APK 파일을 돌려준다. [onProgress] 는 (받은 바이트, 전체 바이트) 를 받는다.
     * 전체 크기를 모르면 전체는 -1 이다. sha256 이 없는 릴리스는 받지 않는다.
     */
    suspend fun download(release: LatestRelease, onProgress: (received: Long, total: Long) -> Unit): File =
        withContext(dispatcher) {
            val expected = release.sha256 ?: throw UpdateFailure("이 버전은 앱에서 받을 수 없습니다. 브라우저로 받으세요.")
            dir.mkdirs()
            val partial = File(dir, UpdateSpec.PARTIAL_NAME)
            val target = File(dir, UpdateSpec.APK_NAME)
            partial.delete()
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                http.get(release.apkUrl, followRedirects = true, timeoutMs = timeoutMs).use { reply ->
                    if (reply.status !in 200..299) {
                        throw UpdateFailure("다운로드 실패 (응답 코드 ${reply.status}). 다시 시도하세요.")
                    }
                    val total = release.size ?: reply.contentLength.takeIf { it > 0L } ?: -1L
                    onProgress(0L, total)
                    partial.outputStream().buffered().use { out ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = reply.body.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            received += read
                            onProgress(received, total)
                        }
                    }
                }
                release.size?.let { size ->
                    if (received != size) throw UpdateFailure("받은 크기가 맞지 않습니다. 다시 받아 주세요.")
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    throw UpdateFailure("받은 파일의 sha256 이 맞지 않습니다. 다시 받아 주세요.")
                }
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                target
            } catch (failure: Throwable) {
                partial.delete()
                throw failure
            }
        }

    private companion object {
        const val BUFFER_BYTES = 8 * 1024
    }
}
