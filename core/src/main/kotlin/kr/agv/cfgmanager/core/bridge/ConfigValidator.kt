package kr.agv.cfgmanager.core.bridge

import kr.agv.cfgmanager.core.util.Ipv4
import kr.agv.cfgmanager.core.util.utf8Length

enum class ValidationSeverity { Error, Warning }

enum class ValidationCode {
    REQUIRED,
    FORMAT,
    RANGE,
    BYTES,
    EDGE_SPACE,
    ZERO_ADDRESS,
    MASK_NONCONTIGUOUS,
    GW_OUTSIDE_SUBNET,
    SUBNET_MISMATCH,
    DUPLICATE_AGV,
}

data class ValidationMessage(
    val key: ConfigKey?,
    val severity: ValidationSeverity,
    val code: ValidationCode,
    val text: String,
)

/** [takenAgv]: 이미 설정된 호기 번호 → "2026-10-02 에 다른 보드에 설정됐습니다" 같은 설명 문장. */
data class ValidationContext(val takenAgv: Map<Int, String> = emptyMap())

data class ValidationReport(val messages: List<ValidationMessage>, val config: BridgeConfig?) {
    val hasErrors: Boolean get() = messages.any { it.severity == ValidationSeverity.Error }

    fun messagesFor(key: ConfigKey): List<ValidationMessage> = messages.filter { it.key == key }
}

/**
 * 설정 입력 검증 (docs/DEVELOPMENT_PLAN.md 3.1 과 브리지 `app_config.c apply()`).
 *
 * 입력은 폼의 원문 문자열이다. 오류가 없으면 [ValidationReport.config] 를 만든다.
 * 길이 제한은 전부 UTF-8 바이트 기준이다 (SSID 32바이트, 비밀번호 8~64바이트).
 *
 * 값 규칙 (앱 정책이 브리지보다 엄격한 곳):
 * - IP 앞자리 0 거부 (브리지는 허용하지만 입력 실수 방지).
 * - IP · 마스크 · 서버 · DNS · rfid_dst 에 0.0.0.0 거부 (브리지에서는 dhcp · auto · off 와 같은 뜻이라 혼동을 막는다).
 * - SSID · 비밀번호의 앞뒤 공백 거부 (브리지가 잘라 버리므로 되읽기가 어긋난다).
 *
 * 빈 비밀번호 입력은 "유지" 다 (null). 비밀번호 없는 AP 는 `-` 를 입력한다.
 */
object ConfigValidator {

    fun validate(raw: Map<ConfigKey, String>, context: ValidationContext = ValidationContext()): ValidationReport {
        val sink = Sink()

        val agv = intField(raw, ConfigKey.AGV, 1..99, sink, "호기 번호는 1~99 입니다.")
        val ssid = ssidField(raw, sink)
        val pass = passField(raw, sink)
        val ip = ipField(raw, sink)
        val mask = addressField(raw, ConfigKey.MASK, sink, required = true, allowZero = false)
        val gw = addressField(raw, ConfigKey.GW, sink, required = true, allowZero = true)
        val dns = optionalAddressField(raw, ConfigKey.DNS, sink)
        val dns2 = optionalAddressField(raw, ConfigKey.DNS2, sink)
        val server = addressField(raw, ConfigKey.SERVER, sink, required = true, allowZero = false)
        val serverPort = intField(raw, ConfigKey.SERVER_PORT, 1..65535, sink, "포트는 1~65535 입니다.")
        val localPort = intField(raw, ConfigKey.LOCAL_PORT, 1..65535, sink, "포트는 1~65535 입니다.")
        val rfidDst = rfidDstField(raw, sink)
        val rfidPort = intField(raw, ConfigKey.RFID_PORT, 1..65535, sink, "포트는 1~65535 입니다.")
        val mainBaud = intField(raw, ConfigKey.MAIN_BAUD, 1200..1000000, sink, "보드레이트는 1200~1000000 입니다.")
        val rfidBaud = intField(raw, ConfigKey.RFID_BAUD, 1200..1000000, sink, "보드레이트는 1200~1000000 입니다.")
        val hbMs = hbField(raw, sink)

        // 서브넷 경고는 주소가 모두 유효할 때만 의미가 있다
        if (ip.value != null && mask != null && server != null) {
            val ipText = ip.value
            if (Ipv4.sameSubnet(ipText, server, mask) == false) {
                sink.warn(
                    ConfigKey.SERVER, ValidationCode.SUBNET_MISMATCH,
                    "IP $ipText 와 서버 $server 가 같은 네트워크가 아닙니다 (마스크 $mask). 게이트웨이를 거칩니다.",
                )
            }
            if (gw != null && gw != "0.0.0.0" && Ipv4.sameSubnet(ipText, gw, mask) == false) {
                sink.warn(
                    ConfigKey.GW, ValidationCode.GW_OUTSIDE_SUBNET,
                    "게이트웨이 $gw 가 IP $ipText 와 다른 네트워크입니다 (마스크 $mask).",
                )
            }
        }
        if (mask != null && Ipv4.isContiguousMask(mask) == false) {
            sink.warn(ConfigKey.MASK, ValidationCode.MASK_NONCONTIGUOUS, "연속되지 않은 마스크입니다. 의도한 값인지 확인하세요.")
        }
        if (agv != null) {
            context.takenAgv[agv]?.let { desc ->
                sink.warn(
                    ConfigKey.AGV, ValidationCode.DUPLICATE_AGV,
                    "${"%02d".format(agv)}호기는 $desc. 교체라면 계속하세요.",
                )
            }
        }

        val messages = sink.items.toList()
        val hasErrors = messages.any { it.severity == ValidationSeverity.Error }
        val complete = !hasErrors &&
            agv != null && ssid != null && ip.parsed && mask != null && gw != null && dns.parsed && dns2.parsed &&
            server != null && serverPort != null && localPort != null && rfidDst.parsed && rfidPort != null &&
            mainBaud != null && rfidBaud != null && hbMs != null
        if (!complete) return ValidationReport(messages, null)

        val (passText, passLength) = pass.value?.let { value ->
            if (value == "-") "" to 0 else value to value.utf8Length()
        } ?: (null to null)
        val config = BridgeConfig(
            agv = agv!!,
            ssid = ssid!!,
            pass = passText,
            passLength = passLength,
            ip = ip.value,
            mask = mask!!,
            gw = gw!!,
            dns = dns.value,
            dns2 = dns2.value,
            server = server!!,
            serverPort = serverPort!!,
            localPort = localPort!!,
            rfidDst = rfidDst.value,
            rfidPort = rfidPort!!,
            mainBaud = mainBaud!!,
            rfidBaud = rfidBaud!!,
            hbMs = hbMs!!,
        )
        return ValidationReport(messages, config)
    }

    // ---- 필드별 규칙 ----

    /** 결과 하나. [parsed] 는 키가 '값 없음(의도한 기본)' 으로 유효한지, [value] 는 표준 값. */
    private class Field<T>(val value: T?, val parsed: Boolean)

    private class Sink {
        val items = mutableListOf<ValidationMessage>()

        fun error(key: ConfigKey, code: ValidationCode, text: String) {
            items += ValidationMessage(key, ValidationSeverity.Error, code, text)
        }

        fun warn(key: ConfigKey, code: ValidationCode, text: String) {
            items += ValidationMessage(key, ValidationSeverity.Warning, code, text)
        }
    }

    private fun text(raw: Map<ConfigKey, String>, key: ConfigKey): String = raw[key].orEmpty()

    private val digitsOnly = Regex("[0-9]{1,9}")

    private fun intField(
        raw: Map<ConfigKey, String>,
        key: ConfigKey,
        range: IntRange,
        sink: Sink,
        rangeText: String,
    ): Int? {
        val s = text(raw, key)
        if (s.isEmpty()) {
            sink.error(key, ValidationCode.REQUIRED, "${key.wire} 값을 입력하세요.")
            return null
        }
        if (!digitsOnly.matches(s)) {
            sink.error(key, ValidationCode.FORMAT, "${key.wire} 는 숫자만 입력하세요.")
            return null
        }
        val value = s.toInt()
        if (value !in range) {
            sink.error(key, ValidationCode.RANGE, rangeText)
            return null
        }
        return value
    }

    private fun hbField(raw: Map<ConfigKey, String>, sink: Sink): Int? {
        val key = ConfigKey.HB_MS
        val s = text(raw, key)
        if (s.isEmpty()) {
            sink.error(key, ValidationCode.REQUIRED, "hb_ms 값을 입력하세요.")
            return null
        }
        if (!digitsOnly.matches(s)) {
            sink.error(key, ValidationCode.FORMAT, "hb_ms 는 숫자만 입력하세요.")
            return null
        }
        val value = s.toInt()
        if (value != 0 && value !in 100..2000) {
            sink.error(key, ValidationCode.RANGE, "heartbeat 주기는 0(끔) 또는 100~2000 ms 입니다.")
            return null
        }
        return value
    }

    private fun ssidField(raw: Map<ConfigKey, String>, sink: Sink): String? {
        val key = ConfigKey.SSID
        val s = text(raw, key)
        if (s.isEmpty()) {
            sink.error(key, ValidationCode.REQUIRED, "Wi-Fi 이름(SSID)을 입력하세요.")
            return null
        }
        if (s.first() == ' ' || s.last() == ' ') {
            sink.error(key, ValidationCode.EDGE_SPACE, "SSID 앞뒤 공백은 저장되지 않습니다. 공백을 지워 주세요.")
            return null
        }
        if (s.utf8Length() > 32) {
            sink.error(key, ValidationCode.BYTES, "SSID 는 32바이트(영문 32자, 한글 약 10자)까지입니다.")
            return null
        }
        return s
    }

    /** 빈 값 = 유지(null, 보내지 않음). `-` = 비밀번호 없음. 나머지는 8~64바이트. */
    private fun passField(raw: Map<ConfigKey, String>, sink: Sink): Field<String> {
        val key = ConfigKey.PASS
        val s = text(raw, key)
        if (s.isEmpty()) return Field(null, parsed = true)
        if (s == "-") return Field("-", parsed = true)
        if (s.first() == ' ' || s.last() == ' ') {
            sink.error(key, ValidationCode.EDGE_SPACE, "비밀번호 앞뒤 공백은 저장되지 않습니다.")
            return Field(null, parsed = false)
        }
        val bytes = s.utf8Length()
        if (bytes < 8 || bytes > 64) {
            sink.error(key, ValidationCode.BYTES, "비밀번호는 8~64바이트입니다 (없으면 - 를 입력). 지금 ${bytes}바이트.")
            return Field(null, parsed = false)
        }
        return Field(s, parsed = true)
    }

    /** IP: `dhcp` 또는 고정 주소. 반환 값 null 은 DHCP. */
    private fun ipField(raw: Map<ConfigKey, String>, sink: Sink): IpValue {
        val key = ConfigKey.IP
        val s = text(raw, key)
        if (s.isEmpty()) {
            sink.error(key, ValidationCode.REQUIRED, "IP 를 입력하거나 dhcp 를 입력하세요.")
            return IpValue(null, parsed = false)
        }
        if (s == "dhcp") return IpValue(null, parsed = true)
        val canonical = Ipv4.canonical(s)
        if (canonical == null) {
            sink.error(key, ValidationCode.FORMAT, "IP 형식이 아닙니다 (예: 192.168.10.107, 앞자리 0 없이).")
            return IpValue(null, parsed = false)
        }
        if (canonical == "0.0.0.0") {
            sink.error(key, ValidationCode.ZERO_ADDRESS, "고정 IP 로 0.0.0.0 은 쓸 수 없습니다. 자동 할당은 dhcp 를 입력하세요.")
            return IpValue(null, parsed = false)
        }
        return IpValue(canonical, parsed = true)
    }

    private class IpValue(val value: String?, val parsed: Boolean)

    private fun addressField(
        raw: Map<ConfigKey, String>,
        key: ConfigKey,
        sink: Sink,
        required: Boolean,
        allowZero: Boolean,
    ): String? {
        val s = text(raw, key)
        if (s.isEmpty()) {
            if (required) sink.error(key, ValidationCode.REQUIRED, "${key.wire} 주소를 입력하세요.")
            return null
        }
        val canonical = Ipv4.canonical(s)
        if (canonical == null) {
            sink.error(key, ValidationCode.FORMAT, "${key.wire} 는 IP 형식이어야 합니다 (앞자리 0 없이).")
            return null
        }
        if (!allowZero && canonical == "0.0.0.0") {
            sink.error(key, ValidationCode.ZERO_ADDRESS, "${key.wire} 에 0.0.0.0 은 쓸 수 없습니다.")
            return null
        }
        return canonical
    }

    /** DNS: 빈 값 또는 `auto` = 자동(null). 0.0.0.0 은 거부(자동은 auto). */
    private fun optionalAddressField(raw: Map<ConfigKey, String>, key: ConfigKey, sink: Sink): Field<String> {
        val s = text(raw, key)
        if (s.isEmpty() || s == "auto") return Field(null, parsed = true)
        val canonical = Ipv4.canonical(s)
        if (canonical == null) {
            sink.error(key, ValidationCode.FORMAT, "${key.wire} 는 IP 형식 또는 auto 입니다.")
            return Field(null, parsed = false)
        }
        if (canonical == "0.0.0.0") {
            sink.error(key, ValidationCode.ZERO_ADDRESS, "${key.wire} 에 0.0.0.0 대신 auto 를 입력하세요.")
            return Field(null, parsed = false)
        }
        return Field(canonical, parsed = true)
    }

    /** rfid_dst: `off` = 보내지 않음(null), 브로드캐스트 255.255.255.255, 또는 IP. */
    private fun rfidDstField(raw: Map<ConfigKey, String>, sink: Sink): Field<String> {
        val key = ConfigKey.RFID_DST
        val s = text(raw, key)
        if (s.isEmpty()) {
            sink.error(key, ValidationCode.REQUIRED, "rfid_dst 값을 입력하세요 (off 또는 IP).")
            return Field(null, parsed = false)
        }
        if (s == "off") return Field(null, parsed = true)
        val canonical = Ipv4.canonical(s)
        if (canonical == null) {
            sink.error(key, ValidationCode.FORMAT, "rfid_dst 는 IP 형식 또는 off 입니다.")
            return Field(null, parsed = false)
        }
        if (canonical == "0.0.0.0") {
            sink.error(key, ValidationCode.ZERO_ADDRESS, "rfid_dst 에 0.0.0.0 대신 off 를 입력하세요.")
            return Field(null, parsed = false)
        }
        return Field(canonical, parsed = true)
    }
}
