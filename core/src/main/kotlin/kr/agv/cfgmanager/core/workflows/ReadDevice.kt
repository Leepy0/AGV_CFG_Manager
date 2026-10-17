package kr.agv.cfgmanager.core.workflows

import kr.agv.cfgmanager.core.bridge.BridgeCommands
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.ChannelState
import kr.agv.cfgmanager.core.bridge.Capabilities
import kr.agv.cfgmanager.core.bridge.DeviceSnapshot
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.bridge.ResponseParsers

/**
 * 장치 읽기 (docs/DEVELOPMENT_PLAN.md 7.2): `cfg` → `stat` → `help` 로 [DeviceSnapshot] 을 만든다.
 * 한 부분이 해석되지 않아도 연결은 유지하고 [DeviceSnapshot.issues] 에 남긴다.
 */
class ReadDevice(
    private val channel: IBridgeChannel,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun read(): DeviceSnapshot {
        val issues = mutableListOf<String>()
        val connected = channel.state.value as? ChannelState.Connected

        val cfgText = channel.exec(BridgeCommands.cfg())
        val cfg = parseConfig(cfgText)
        if (cfg == null) issues += "cfg 출력을 해석하지 못했습니다 — 콘솔에서 확인하세요."

        val statText = channel.exec(BridgeCommands.stat())
        val status = ResponseParsers.parseStat(statText)
        if (status == null) issues += "stat 출력을 해석하지 못했습니다 — 콘솔에서 확인하세요."

        val help = try {
            ResponseParsers.parseHelp(channel.exec(BridgeCommands.help()))
        } catch (timeout: BridgeChannelException.Timeout) {
            issues += "help 응답이 없어 선택 기능을 확인하지 못했습니다."
            Capabilities()
        }
        val capabilities = help.copy(dnsKeys = cfg?.dnsSupported ?: false)

        return DeviceSnapshot(
            deviceId = connected?.deviceId ?: "",
            name = connected?.name ?: status?.setup?.name,
            fw = status?.fw ?: connected?.fw,
            mac = status?.wifi?.mac,
            config = cfg?.config,
            cfgState = cfg?.state,
            status = status,
            capabilities = capabilities,
            readAt = nowMillis(),
            issues = issues,
        )
    }

    /**
     * `cfg` 파싱. 출력이 `{` 로 시작하면 JSON 경로(BR-1), 아니면 텍스트 경로.
     * JSON 경로는 아직 브리지에 없어 [JsonConfigReader] 가 null 을 주고 텍스트 파서로 넘어간다.
     */
    private fun parseConfig(text: String): ResponseParsers.CfgReport? {
        val fromJson = if (text.trimStart().startsWith("{")) JsonConfigReader.read(text) else null
        return fromJson ?: ResponseParsers.parseCfg(text)
    }
}

/**
 * `cfg json` (BR-1) 해석 자리. TODO: 브리지가 `cfg json` 을 지원하면 구현한다.
 * 지금은 항상 null 이라 텍스트 경로로 폴백한다.
 */
internal object JsonConfigReader {
    @Suppress("UNUSED_PARAMETER")
    fun read(text: String): ResponseParsers.CfgReport? = null
}
