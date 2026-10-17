package kr.agv.cfgmanager.core.bridge

/** Wi-Fi 끊김 사유 코드 해석 (docs/DEVELOPMENT_PLAN.md 9.3, ESP-IDF `wifi_err_reason_t`). */
object WifiReason {

    data class Info(val code: Int, val name: String?, val message: String)

    private val table: Map<Int, Pair<String, String>> = mapOf(
        2 to ("AUTH_EXPIRE" to "인증 만료 — AP 쪽 설정 · 신호 확인"),
        8 to ("ASSOC_LEAVE" to "브리지가 스스로 끊음(재부팅 · 설정 변경) — 정상 과정"),
        15 to ("4WAY_HANDSHAKE_TIMEOUT" to "비밀번호가 틀렸을 가능성 — 비밀번호 확인"),
        200 to ("BEACON_TIMEOUT" to "신호 끊김 — AP 거리 · RSSI 확인"),
        201 to ("NO_AP_FOUND" to "AP 를 찾지 못함 — SSID 철자, 2.4GHz 인지, 채널 1~13 인지, AP 전원 확인"),
        202 to ("AUTH_FAIL" to "인증 실패 — 비밀번호 · 보안 방식 확인"),
        203 to ("ASSOC_FAIL" to "연결 거부 — AP 의 최대 접속 수 · MAC 필터 확인"),
        204 to ("HANDSHAKE_TIMEOUT" to "비밀번호 오류 가능성 — 비밀번호 확인"),
        205 to ("CONNECTION_FAIL" to "연결 실패 — AP 상태 확인"),
        210 to ("NO_AP_FOUND_W_COMPATIBLE_SECURITY" to "보안 방식 불일치 — AP 가 WPA2-PSK 를 지원하는지 (WPA3 전용 · 엔터프라이즈 불가)"),
        211 to ("NO_AP_FOUND_IN_AUTHMODE_THRESHOLD" to "같은 SSID 가 있지만 보안 방식이 다름 — 비밀번호 없는 AP 면 비밀번호를 - 로 설정"),
        212 to ("NO_AP_FOUND_IN_RSSI_THRESHOLD" to "신호가 너무 약함 — AP 를 가까이"),
    )

    /** 사유 코드 하나를 사용자 문구로. 0 은 정상, 표에 없는 코드는 코드 그대로 안내. */
    fun describe(code: Int): Info {
        if (code == 0) return Info(code, null, "정상")
        val entry = table[code] ?: return Info(
            code, null, "ESP-IDF 사유 코드 $code — 콘솔 로그에서 확인",
        )
        return Info(code, entry.first, entry.second)
    }
}
