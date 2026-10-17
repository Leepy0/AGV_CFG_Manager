package kr.agv.cfgmanager.core.profiles

import kotlinx.serialization.json.JsonObject
import kr.agv.cfgmanager.core.bridge.ConfigKey

/**
 * 현장 프로필 (부록 B-1). Wi-Fi 비밀번호는 여기 두지 않고 SSID 라이브러리에서 찾는다.
 * [dns] · [dns2] 는 `auto` 또는 IP, [rfidDst] 는 `off` 또는 IP 를 그대로 담는다.
 * [extras] 는 이 앱 버전이 모르는 필드를 보관해 저장할 때 되돌려 쓴다.
 */
data class Profile(
    val id: String,
    val name: String,
    val notes: String,
    val createdAt: String,
    val updatedAt: String,
    val lastUsedAt: String?,
    val ssid: String,
    val ipRule: IpRule,
    val mask: String,
    val gw: String,
    val dns: String,
    val dns2: String,
    val server: String,
    val serverPort: Int,
    val localPort: Int,
    val rfidDst: String,
    val rfidPort: Int,
    val mainBaud: Int,
    val rfidBaud: Int,
    val hbMs: Int,
    val blePassword: String,
    val extras: JsonObject = JsonObject(emptyMap()),
) {
    /**
     * 호기 번호 [agv] 에 맞춘 설정 폼 초깃값. 비밀번호는 [wifiPassword] (없으면 빈 값 = 유지).
     * IP 는 규칙으로 계산되지 않으면(직접 입력) 빈 값으로 둔다.
     */
    fun draft(agv: Int, wifiPassword: String?): Map<ConfigKey, String> {
        val ip = when (val rule = ipRule) {
            IpRule.Dhcp -> "dhcp"
            IpRule.Manual -> ""
            is IpRule.Offset -> (IpRuleCalculator.compute(rule, agv) as? IpComputation.Ok)?.ip.orEmpty()
        }
        return linkedMapOf(
            ConfigKey.AGV to agv.toString(),
            ConfigKey.SSID to ssid,
            ConfigKey.PASS to wifiPassword.orEmpty(),
            ConfigKey.IP to ip,
            ConfigKey.MASK to mask,
            ConfigKey.GW to gw,
            ConfigKey.DNS to dns,
            ConfigKey.DNS2 to dns2,
            ConfigKey.SERVER to server,
            ConfigKey.SERVER_PORT to serverPort.toString(),
            ConfigKey.LOCAL_PORT to localPort.toString(),
            ConfigKey.RFID_DST to rfidDst,
            ConfigKey.RFID_PORT to rfidPort.toString(),
            ConfigKey.MAIN_BAUD to mainBaud.toString(),
            ConfigKey.RFID_BAUD to rfidBaud.toString(),
            ConfigKey.HB_MS to hbMs.toString(),
        )
    }
}

/** `profiles.json` 한 파일 (부록 B-1). */
data class ProfileDocument(
    val version: Int,
    val lastProfileId: String?,
    val profiles: List<Profile>,
    val extras: JsonObject = JsonObject(emptyMap()),
)
