package kr.agv.cfgmanager.core.history

/** 이력 화면용 문구와 검색. 순수 함수 (JVM 테스트 대상). */
object HistoryView {

    /** 이력 `result` 코드를 사람이 읽을 문구로 (docs/DEVELOPMENT_PLAN.md 부록 B-3, 9장). */
    fun resultLabel(result: String): String = when {
        result == "ok" -> "성공"
        result.startsWith("wifi_down:") -> "Wi-Fi 끊김 (${result.removePrefix("wifi_down:")})"
        result.startsWith("invalid:") -> "값 거부 (${result.removePrefix("invalid:")})"
        result == "save_failed" -> "저장 실패"
        result == "reboot_refused" -> "재부팅 거부 (메인보드 작업 중)"
        result == "reconnect_timeout" -> "재부팅 뒤 재연결 실패"
        result == "wifi_ip_mismatch" -> "Wi-Fi IP 불일치"
        result == "readback_mismatch" -> "되읽기 불일치"
        result == "timeout" -> "응답 없음"
        result == "disconnected" -> "연결 끊김"
        result == "cancelled" -> "취소"
        result == "no_change" -> "바뀐 값 없음"
        result == "not_ready" -> "연결되어 있지 않음"
        else -> "기타 ($result)"
    }

    fun isSuccess(result: String): Boolean = result == "ok"

    /**
     * 검색 · 기간 필터. 최신 건이 위로 오도록 정렬한다.
     * [query]: 프로필 이름 · 장치 이름 · IP · SSID 에 부분 일치(대소문자 무시), 호기 번호는 "7" 또는 "07" 도 맞는다.
     * [sinceIso]: 이 시각(UTC ISO 문자열, 사전순 비교) 이후 건만. null 이면 기간 제한 없음.
     */
    fun filter(entries: List<HistoryEntry>, query: String, sinceIso: String?): List<HistoryEntry> {
        val q = query.trim()
        return entries
            .filter { sinceIso == null || it.at >= sinceIso }
            .filter { q.isEmpty() || matches(it, q) }
            .sortedByDescending { it.at }
    }

    private fun matches(entry: HistoryEntry, query: String): Boolean {
        val needle = query.lowercase()
        val agvText = entry.agv?.let { "%02d".format(it) }.orEmpty()
        val agvPlain = entry.agv?.toString().orEmpty()
        return listOf(entry.profileName.orEmpty(), entry.deviceName.orEmpty(), entry.ip.orEmpty(), entry.ssid.orEmpty())
            .any { it.lowercase().contains(needle) } ||
            (agvText.isNotEmpty() && (agvText == needle || agvPlain == needle))
    }
}
