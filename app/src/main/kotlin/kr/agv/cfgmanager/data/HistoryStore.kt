package kr.agv.cfgmanager.data

import kr.agv.cfgmanager.core.history.HistoryCodec
import kr.agv.cfgmanager.core.history.HistoryEntry
import java.io.File
import java.io.FileOutputStream

/**
 * 작업 이력 저장소 (docs/DEVELOPMENT_PLAN.md F-H1, 6.3). `history.jsonl` 에 한 줄씩 추가만 한다.
 * 추가 쓰기는 한 줄 단위라 임시 파일 · rename 없이 끝에 붙이고 디스크에 동기화한다.
 * 읽기는 깨진 줄을 건너뛴다. 이력 탭 UI 는 Phase 4 에서 붙인다.
 */
class HistoryStore(private val file: File) {

    private val lock = Any()

    /** 한 건을 끝에 붙인다. 디렉터리가 없으면 만든다. 실패하면 [java.io.IOException] 을 던진다. */
    fun append(entry: HistoryEntry) {
        val line = HistoryCodec.encodeLine(entry) + "\n"
        synchronized(lock) {
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).use { out ->
                out.write(line.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
        }
    }

    /** 저장된 모든 건. 파일이 없으면 빈 목록. */
    fun readAll(): List<HistoryEntry> = synchronized(lock) {
        if (!file.exists()) emptyList() else HistoryCodec.decodeLines(file.readText(Charsets.UTF_8))
    }
}
