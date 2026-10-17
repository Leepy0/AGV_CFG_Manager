package kr.agv.cfgmanager.core.workflows

/**
 * 연속 세팅 요약 (docs/DEVELOPMENT_PLAN.md F-B3). 공유 시트로 보내는 평문 표를 만든다.
 * 열: 호기 · 장치 이름 · 결과. 비밀번호와 IP 는 넣지 않는다.
 */
object SessionSummary {

    fun toText(done: List<BatchDone>, date: String): String = buildString {
        appendLine("AGV 세팅 요약 · $date")
        appendLine("설정한 호기 ${done.size}대")
        if (done.isEmpty()) return@buildString
        appendLine()
        appendLine("호기\t장치 이름\t결과")
        done.forEach { entry ->
            val agv = "%02d".format(entry.agv)
            appendLine("${agv}호기\t${entry.deviceName ?: "-"}\t${resultText(entry.result)}")
        }
    }

    private fun resultText(result: String): String = if (result == "ok") "성공" else "확인 필요 ($result)"
}
