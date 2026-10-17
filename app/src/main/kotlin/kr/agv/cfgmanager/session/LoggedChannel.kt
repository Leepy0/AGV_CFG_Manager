package kr.agv.cfgmanager.session

import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kotlin.time.Duration

/**
 * 보낸 명령을 콘솔 버퍼에 남기는 채널 래퍼. 받은 원문은 채널의 `raw` 흐름을 세션이 따로 모은다.
 * 비밀번호는 [ConsoleLog.sent] 가 가린다.
 */
internal class LoggedChannel(
    private val inner: IBridgeChannel,
    private val log: ConsoleLog,
    private val clock: () -> Long,
    private val onChange: () -> Unit,
) : IBridgeChannel by inner {

    override suspend fun exec(line: String, timeout: Duration): String {
        log.sent(line, clock())
        onChange()
        return inner.exec(line, timeout)
    }

    override suspend fun execThenDisconnect(line: String): String {
        log.sent(line, clock())
        onChange()
        return inner.execThenDisconnect(line)
    }
}
