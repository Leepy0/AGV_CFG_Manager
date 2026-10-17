package kr.agv.cfgmanager.core.testsupport

import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.bridge.ConfigValidator
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.bridge.ChannelKind
import kr.agv.cfgmanager.core.bridge.ChannelState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/** 테스트 리소스 `fixtures/<name>` 을 원문 그대로(CRLF 유지) 읽는다. */
fun fixture(name: String): String {
    val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/fixtures/$name")) { "fixture 없음: $name" }
    return stream.use { it.readBytes().toString(Charsets.UTF_8) }
}

/** 시험용 설정 입력. 호스트는 192.168.10.x 예시만 쓴다. Wi-Fi 비밀번호는 빌드 기본값 수준의 임시 값. */
fun validRaw(vararg overrides: Pair<ConfigKey, String>): Map<ConfigKey, String> {
    val base = linkedMapOf(
        ConfigKey.AGV to "7",
        ConfigKey.SSID to "AGV_WIFI",
        ConfigKey.PASS to "agvpass12",
        ConfigKey.IP to "192.168.10.107",
        ConfigKey.MASK to "255.255.255.0",
        ConfigKey.GW to "192.168.10.1",
        ConfigKey.DNS to "192.168.10.1",
        ConfigKey.DNS2 to "auto",
        ConfigKey.SERVER to "192.168.10.10",
        ConfigKey.SERVER_PORT to "50000",
        ConfigKey.LOCAL_PORT to "50000",
        ConfigKey.RFID_DST to "255.255.255.255",
        ConfigKey.RFID_PORT to "50001",
        ConfigKey.MAIN_BAUD to "115200",
        ConfigKey.RFID_BAUD to "9600",
        ConfigKey.HB_MS to "1000",
    )
    overrides.forEach { (k, v) -> base[k] = v }
    return base
}

/** 검증을 통과한 목표 설정. 실패하면 테스트가 바로 실패하도록 예외를 던진다. */
fun validTarget(vararg overrides: Pair<ConfigKey, String>): BridgeConfig {
    val report = ConfigValidator.validate(validRaw(*overrides))
    return checkNotNull(report.config) { "검증 실패: ${report.messages}" }
}

/** 한 응답 조각 단위로 되돌려 주는 채널 래퍼: 특정 명령의 응답을 바꾸거나 막는다. */
class TamperingChannel(
    private val inner: IBridgeChannel,
    private val replace: (command: String, reply: String) -> String,
) : IBridgeChannel {
    override val kind: ChannelKind get() = inner.kind
    override val state: StateFlow<ChannelState> get() = inner.state
    override val raw: SharedFlow<String> get() = inner.raw

    override suspend fun exec(line: String, timeout: Duration): String =
        replace(line, inner.exec(line, timeout))

    override suspend fun execThenDisconnect(line: String): String = inner.execThenDisconnect(line)

    override suspend fun waitReady(timeout: Duration): Boolean = inner.waitReady(timeout)

    override suspend fun disconnect() = inner.disconnect()
}
