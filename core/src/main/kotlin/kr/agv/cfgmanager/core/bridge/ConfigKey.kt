package kr.agv.cfgmanager.core.bridge

/**
 * 브리지 `set <key> <value>` 의 키 16개 (docs/DEVELOPMENT_PLAN.md 3.1, 브리지 `app_config.c apply()`).
 * 선언 순서가 곧 적용 시 전송 순서(7.1)다.
 */
enum class ConfigKey(val wire: String) {
    AGV("agv"),
    SSID("ssid"),
    PASS("pass"),
    IP("ip"),
    MASK("mask"),
    GW("gw"),
    DNS("dns"),
    DNS2("dns2"),
    SERVER("server"),
    SERVER_PORT("server_port"),
    LOCAL_PORT("local_port"),
    RFID_DST("rfid_dst"),
    RFID_PORT("rfid_port"),
    MAIN_BAUD("main_baud"),
    RFID_BAUD("rfid_baud"),
    HB_MS("hb_ms");

    companion object {
        /** 0.5.5 부터 있는 키. 구버전 펌웨어에서는 보내지 않는다. */
        val DNS_KEYS: Set<ConfigKey> = setOf(DNS, DNS2)

        fun fromWire(wire: String): ConfigKey? = entries.firstOrNull { it.wire == wire }
    }
}
