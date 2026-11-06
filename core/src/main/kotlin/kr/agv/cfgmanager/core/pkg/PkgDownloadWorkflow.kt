package kr.agv.cfgmanager.core.pkg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.BridgeCommands
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

sealed interface PkgDownloadOutcome {
    /** 크기와 sha256 이 맞는 패키지. */
    data class Done(val file: File) : PkgDownloadOutcome

    /** 링크가 끊겼다. 파일이 남아 있어 다시 실행하면 이어받는다. */
    data object Disconnected : PkgDownloadOutcome
    data object Busy : PkgDownloadOutcome

    /** 받는 중 슬롯이 다른 패키지로 바뀌었다. 파일은 지웠다. */
    data object SlotChanged : PkgDownloadOutcome

    /** 다 받았지만 sha256 이 다르다. 파일은 지웠다. */
    data object Corrupt : PkgDownloadOutcome

    /** 받은 내용을 파일에 쓰지 못했다 (공간 부족 등). 파일은 남아 있다. */
    data object DiskFailed : PkgDownloadOutcome

    /** 재시도 초과, 거절 코드. 파일은 남아 있다. */
    data class Failed(val reason: String) : PkgDownloadOutcome
}

/**
 * 슬롯 패키지를 [part] 파일에 이어받는다. 파일 길이가 곧 받은 위치다.
 * 호출 측이 세션의 독점 구간 안에서 [IBridgeChannel] 을 넘긴다. 메인 스레드에서 불러도 된다.
 */
object PkgDownloadWorkflow {
    private const val CHUNK_BYTES = 512L
    private const val MAX_FAILURES = 3
    private val CHUNK_TIMEOUT = 5.seconds

    suspend fun run(
        channel: IBridgeChannel,
        slot: PkgSlot,
        part: File,
        onProgress: (done: Long, total: Long) -> Unit,
    ): PkgDownloadOutcome = try {
        download(channel, slot, part, onProgress)
    } catch (_: IOException) {
        PkgDownloadOutcome.DiskFailed
    }

    private suspend fun download(
        channel: IBridgeChannel,
        slot: PkgSlot,
        part: File,
        onProgress: (done: Long, total: Long) -> Unit,
    ): PkgDownloadOutcome {
        var done = withContext(Dispatchers.IO) { resumeLength(part, slot.size) }
        onProgress(done, slot.size)
        var failures = 0
        var idDiffered = false
        while (done < slot.size) {
            val len = minOf(CHUNK_BYTES, slot.size - done).toInt()
            val reply = try {
                channel.exec(BridgeCommands.pkgRead(slot.index, done, len), CHUNK_TIMEOUT)
            } catch (_: BridgeChannelException.Timeout) {
                null
            } catch (_: BridgeChannelException.Disconnected) {
                return PkgDownloadOutcome.Disconnected
            } catch (_: BridgeChannelException.NotConnected) {
                return PkgDownloadOutcome.Disconnected
            }
            val chunk = reply?.let(::parsePkgChunk) ?: PkgChunk.Bad
            if (chunk is PkgChunk.Refused) {
                return if (chunk.code == "busy") PkgDownloadOutcome.Busy else PkgDownloadOutcome.Failed(chunk.code)
            }
            val data = (chunk as? PkgChunk.Data)?.takeIf { it.off == done }
            // id 는 CRC 밖이라 한 번은 줄 깨짐일 수 있다. 같은 오프셋을 다시 읽어 두 번 연속일 때만 믿는다.
            if (data != null && data.id != slot.id) {
                if (idDiffered) {
                    withContext(Dispatchers.IO) { part.delete() }
                    return PkgDownloadOutcome.SlotChanged
                }
                idDiffered = true
                continue
            }
            idDiffered = false
            if (data == null || data.bytes.isEmpty() || done + data.bytes.size > slot.size) {
                if (++failures >= MAX_FAILURES) return PkgDownloadOutcome.Failed("청크 오류")
                continue
            }
            val at = done
            withContext(Dispatchers.IO) { RandomAccessFile(part, "rw").use { appendWhole(it, at, data.bytes) } }
            done += data.bytes.size
            failures = 0
            onProgress(done, slot.size)
        }
        return withContext(Dispatchers.IO) {
            if (sha256Hex(part) == slot.sha256) {
                PkgDownloadOutcome.Done(part)
            } else {
                part.delete()
                PkgDownloadOutcome.Corrupt
            }
        }
    }

    /** 이어받을 길이. 패키지보다 긴 파일은 믿을 수 없어 비운다. */
    private fun resumeLength(part: File, size: Long): Long {
        part.parentFile?.mkdirs()
        if (part.length() <= size) return part.length()
        part.writeBytes(ByteArray(0))
        return 0
    }

    /** [at] 위치에 청크 하나를 쓴다. 실패하면 [at] 길이로 되돌려 청크 단위를 지킨다. */
    internal fun appendWhole(file: RandomAccessFile, at: Long, bytes: ByteArray) {
        try {
            file.seek(at)
            file.write(bytes)
        } catch (e: IOException) {
            file.setLength(at)
            throw e
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.forEachBlock { buffer, read -> digest.update(buffer, 0, read) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
