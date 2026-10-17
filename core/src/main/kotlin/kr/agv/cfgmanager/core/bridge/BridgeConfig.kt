package kr.agv.cfgmanager.core.bridge

/**
 * 브리지 설정 한 벌(불변). 장치에서 읽은 값과 사용자가 입력한 목표 값에 같은 타입을 쓴다.
 *
 * 표기 규칙 (값이 없을 때 쓰는 뜻):
 * - [ip] null = DHCP (wire `dhcp`)
 * - [dns] · [dns2] null = 자동 (wire `auto`)
 * - [rfidDst] null = 보내지 않음 (wire `off`)
 * - [pass] null = 모름(장치는 길이만 보고) 또는 이번에는 보내지 않음(유지). 빈 문자열 = 비밀번호 없음(wire `-`)
 */
data class BridgeConfig(
    val agv: Int,
    val ssid: String,
    val pass: String?,
    val passLength: Int?,
    val ip: String?,
    val mask: String,
    val gw: String,
    val dns: String?,
    val dns2: String?,
    val server: String,
    val serverPort: Int,
    val localPort: Int,
    val rfidDst: String?,
    val rfidPort: Int,
    val mainBaud: Int,
    val rfidBaud: Int,
    val hbMs: Int,
) {
    /** 키 하나의 표준 표기. 비밀번호는 길이만 나타낸다. */
    fun canonical(key: ConfigKey): String? = when (key) {
        ConfigKey.AGV -> agv.toString()
        ConfigKey.SSID -> ssid
        ConfigKey.PASS -> passLength?.toString()
        ConfigKey.IP -> ip ?: "dhcp"
        ConfigKey.MASK -> mask
        ConfigKey.GW -> gw
        ConfigKey.DNS -> dns ?: "auto"
        ConfigKey.DNS2 -> dns2 ?: "auto"
        ConfigKey.SERVER -> server
        ConfigKey.SERVER_PORT -> serverPort.toString()
        ConfigKey.LOCAL_PORT -> localPort.toString()
        ConfigKey.RFID_DST -> rfidDst ?: "off"
        ConfigKey.RFID_PORT -> rfidPort.toString()
        ConfigKey.MAIN_BAUD -> mainBaud.toString()
        ConfigKey.RFID_BAUD -> rfidBaud.toString()
        ConfigKey.HB_MS -> hbMs.toString()
    }

    /** `set` 명령에 실어 보낼 값. 비밀번호를 보내지 않으면(pass == null) null. */
    fun wireValue(key: ConfigKey): String? = when (key) {
        ConfigKey.PASS -> pass?.let { if (it.isEmpty()) "-" else it }
        else -> canonical(key)
    }

    /**
     * [other] (보통 장치의 현재 설정)와 비교해 달라진 키. 비밀번호는 목표 값이 있을 때만 변경으로 본다
     * (장치는 비밀번호를 보여 주지 않으므로 입력했다면 바꾸는 것으로 취급).
     */
    fun diff(other: BridgeConfig): Set<ConfigKey> =
        ConfigKey.entries.filter { key -> isChanged(key, other) }.toSet()

    private fun isChanged(key: ConfigKey, other: BridgeConfig): Boolean =
        if (key == ConfigKey.PASS) pass != null && pass != other.pass
        else canonical(key) != other.canonical(key)
}
