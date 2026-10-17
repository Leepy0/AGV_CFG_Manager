package kr.agv.cfgmanager.core.history

/**
 * 이력에서 얻는 호기 번호 힌트 (docs/DEVELOPMENT_PLAN.md F-E6).
 * "저장됨" 으로 볼 결과: `ok`, `wifi_down:*`(저장은 끝남), `reconnect_timeout`, `reboot_refused`.
 */
object AgvHints {

    /** 설정이 장치에 저장된 결과인지. */
    fun savedToDevice(result: String): Boolean =
        result == "ok" || result.startsWith("wifi_down:") || result == "reconnect_timeout" || result == "reboot_refused"

    /**
     * 호기 번호 → 이미 다른 보드에 설정된 설명 ([kr.agv.cfgmanager.core.bridge.ValidationContext.takenAgv] 로 넘긴다).
     * 같은 BLE 주소이거나 MAC 이 같으면 "같은 보드" 로 보고 제외한다(교체가 아니라 재설정이다).
     * 같은 번호가 여럿이면 가장 최근 것을 쓴다. 설명은 날짜(at 앞 10자)로 만든다.
     */
    fun takenAgv(entries: List<HistoryEntry>, currentAddress: String?, currentMac: String?): Map<Int, String> {
        val latest = HashMap<Int, HistoryEntry>()
        for (entry in entries) {
            val agv = entry.agv ?: continue
            if (!savedToDevice(entry.result)) continue
            if (currentAddress != null && entry.bleAddress.equals(currentAddress, ignoreCase = true)) continue
            if (currentMac != null && entry.mac != null && entry.mac.equals(currentMac, ignoreCase = true)) continue
            val known = latest[agv]
            if (known == null || entry.at > known.at) latest[agv] = entry
        }
        return latest.mapValues { (_, entry) -> "${entry.at.take(10)} 에 다른 보드에 설정했습니다" }
    }

    /**
     * BLE 주소(대문자) → 그 보드에 마지막으로 저장한 호기 번호. 보드 목록의 "이력: 07호기" 표식에 쓴다.
     * 저장되지 않은 기록(실패 등)은 무시한다.
     */
    fun latestAgvByAddress(entries: List<HistoryEntry>): Map<String, Int> {
        val latest = HashMap<String, HistoryEntry>()
        for (entry in entries) {
            val agv = entry.agv ?: continue
            if (!savedToDevice(entry.result) || entry.bleAddress == null) continue
            val key = entry.bleAddress.uppercase()
            val known = latest[key]
            if (known == null || entry.at >= known.at) latest[key] = entry
        }
        return latest.mapValues { (_, entry) -> entry.agv!! }
    }

    /** 프로필 안에서 다음 호기 번호 제안 (최대 저장 번호 + 1). 기록이 없거나 99 를 넘으면 null. */
    fun nextAgvFor(profileId: String?, entries: List<HistoryEntry>): Int? {
        if (profileId == null) return null
        val max = entries
            .filter { it.profileId == profileId && it.agv != null && savedToDevice(it.result) }
            .maxOfOrNull { it.agv!! } ?: return null
        return if (max >= 99) null else max + 1
    }
}
