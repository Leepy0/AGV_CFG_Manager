package kr.agv.cfgmanager.ble

import kr.agv.cfgmanager.session.BoardName
import kr.agv.cfgmanager.session.BoardRow

/**
 * 주소별 최신 광고만 남기는 스캔 표 (docs/DEVELOPMENT_PLAN.md 8.2).
 * 스캔 콜백 스레드와 화면 스레드가 같이 쓰므로 동기화한다.
 * 이름이 `AGVBR-` 로 시작하지 않는 기기는 넣지 않는다.
 */
class ScanTable(private val keepMs: Long = KEEP_MS) {
    private val rows = LinkedHashMap<String, BoardRow>()

    @Synchronized
    fun record(address: String, name: String, rssi: Int, nowMs: Long) {
        if (!BoardName.isBoardName(name)) return
        rows[address] = BoardRow(address = address, name = name, rssi = rssi, lastSeenMs = nowMs)
    }

    /** 신호 내림차순 목록. [keepMs] 넘게 안 보인 기기는 표에서 뺀다. */
    @Synchronized
    fun snapshot(nowMs: Long): List<BoardRow> {
        rows.values.removeAll { nowMs - it.lastSeenMs > keepMs }
        return sortRows(rows.values)
    }

    @Synchronized
    fun clear() {
        rows.clear()
    }

    companion object {
        /** 이 시간 이상 안 보이면 흐리게 표시한다. */
        const val STALE_MS = 5_000L
        const val KEEP_MS = 30_000L
    }
}

/** 신호 내림차순, 같으면 이름순. */
fun sortRows(rows: Collection<BoardRow>): List<BoardRow> =
    rows.sortedWith(compareByDescending<BoardRow> { it.rssi }.thenBy { it.name })

fun BoardRow.isStale(nowMs: Long): Boolean = nowMs - lastSeenMs >= ScanTable.STALE_MS

/** 신호 등급 (RSSI dBm). 막대 수와 글자를 함께 쓴다. */
enum class SignalGrade(val bars: Int, val label: String) {
    Strong(3, "강함"),
    Medium(2, "보통"),
    Weak(1, "약함"),
    ;

    companion object {
        fun of(rssi: Int): SignalGrade = when {
            rssi >= -60 -> Strong
            rssi >= -75 -> Medium
            else -> Weak
        }
    }
}
