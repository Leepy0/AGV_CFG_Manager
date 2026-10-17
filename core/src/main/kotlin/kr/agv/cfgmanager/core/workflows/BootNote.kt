package kr.agv.cfgmanager.core.workflows

import kr.agv.cfgmanager.core.bridge.BridgeStatus
import java.util.Locale

/**
 * 재부팅 확인 메모 (docs/DEVELOPMENT_PLAN.md F-D5). 적용 이력의 메모 칸에 한 줄로 남는다.
 * 재부팅 요청부터 재연결까지 걸린 시간과, 재연결 직후 첫 `stat` 의 Wi-Fi 상태를 적는다.
 */
object BootNote {

    fun ok(elapsedMs: Long, firstStat: BridgeStatus?): String =
        "재연결 ${seconds(elapsedMs)} · 첫 stat ${describe(firstStat)}"

    fun timeout(elapsedMs: Long): String = "재연결 시간 초과 (${seconds(elapsedMs)})"

    fun describe(status: BridgeStatus?): String {
        val wifi = status?.wifi ?: return "응답 없음"
        return buildString {
            append("wifi ").append(if (wifi.up) "연결" else "끊김")
            wifi.ip?.let { append(" ").append(it) }
            wifi.rssi?.let { append(" ").append(it).append("dBm") }
        }
    }

    private fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.1f초", ms / 1000.0)
}
