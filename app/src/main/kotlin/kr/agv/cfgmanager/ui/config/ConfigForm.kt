package kr.agv.cfgmanager.ui.config

import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.bridge.ConfigValidator
import kr.agv.cfgmanager.core.bridge.ValidationContext
import kr.agv.cfgmanager.core.bridge.ValidationSeverity
import kr.agv.cfgmanager.core.util.Ipv4

/** IP 모드. [Rule] 은 프로필(Phase 4) 전까지 선택할 수 없다. */
enum class IpMode { Rule, Dhcp, Manual }

/**
 * 설정 폼의 입력 원문 (16개 키). IP 는 DHCP 모드면 `dhcp`, DNS 는 `auto`, rfid_dst 는 `off` 를 뜻한다.
 * 비밀번호는 입력이 비면 "유지" 다 (docs/DEVELOPMENT_PLAN.md 3.1, 7.1).
 */
data class ConfigDraft(
    val values: Map<ConfigKey, String>,
    val ipMode: IpMode,
) {
    fun raw(key: ConfigKey): String = values[key].orEmpty()

    /** 키 하나를 바꾼다. IP 를 직접 고치면 모드는 직접 입력이 된다. */
    fun with(key: ConfigKey, text: String): ConfigDraft {
        val mode = if (key == ConfigKey.IP && text != DHCP) IpMode.Manual else ipMode
        return copy(values = values + (key to text), ipMode = mode)
    }

    /** IP 모드를 바꾼다. [IpMode.Rule] 은 무시한다. */
    fun withIpMode(mode: IpMode): ConfigDraft = when (mode) {
        IpMode.Rule -> this
        IpMode.Dhcp -> copy(ipMode = IpMode.Dhcp, values = values + (ConfigKey.IP to DHCP))
        IpMode.Manual -> copy(
            ipMode = IpMode.Manual,
            values = values + (ConfigKey.IP to if (raw(ConfigKey.IP) == DHCP) "" else raw(ConfigKey.IP)),
        )
    }

    /** 프로필 값 주입 (Phase 4 자리). 호기 번호는 여기서 바꾸지 않는다. */
    fun withProfileValues(incoming: Map<ConfigKey, String>): ConfigDraft {
        var next = this
        incoming.forEach { (key, text) -> if (key != ConfigKey.AGV) next = next.with(key, text) }
        return next
    }

    companion object {
        const val DHCP = "dhcp"
        const val AUTO = "auto"
        const val OFF = "off"
    }
}

/** 항목 아래 한 줄 안내. [key] 가 null 이면 폼 전체 안내. */
data class FieldMessage(val key: ConfigKey?, val isError: Boolean, val text: String)

/** 검증 결과. [config] 는 오류가 없고 모든 값이 채워졌을 때만 있다. */
data class FormCheck(val messages: List<FieldMessage>, val config: BridgeConfig?) {
    val errorCount: Int get() = messages.count { it.isError }

    /** 항목 아래에 보일 문구: 오류가 우선, 없으면 경고. */
    fun messageFor(key: ConfigKey): FieldMessage? =
        messages.firstOrNull { it.key == key && it.isError } ?: messages.firstOrNull { it.key == key }
}

/** 폼 상태 ↔ BridgeConfig 변환, 변경 계산, 검증. 화면과 Android 에 의존하지 않는다. */
object ConfigForm {

    /** 장치에서 읽은 설정으로 폼 초깃값을 만든다. 비밀번호는 비운다(유지). */
    fun fromDevice(device: BridgeConfig): ConfigDraft {
        val values = ConfigKey.entries.associateWith { key ->
            if (key == ConfigKey.PASS) "" else device.canonical(key).orEmpty()
        }
        return ConfigDraft(values, ipMode = if (device.ip == null) IpMode.Dhcp else IpMode.Manual)
    }

    /** 검증 입력. SSID · 비밀번호는 앞뒤 공백을 그대로 둬서 검증이 알리게 하고, 나머지는 다듬는다. */
    fun validatorInput(draft: ConfigDraft): Map<ConfigKey, String> = draft.values.mapValues { (key, text) ->
        if (key == ConfigKey.SSID || key == ConfigKey.PASS) text else text.trim()
    }

    /**
     * 코어 검증 + 앱 추가 검사 (F-E3: IP 가 GW · 서버와 같지 않은지, 마지막 옥텟 0/255 경고).
     * [context.takenAgv] 는 이력에서 온 "다른 보드가 쓴 호기" 경고다. [passwordRequired] 면 비밀번호 칸에 오류를 붙인다 (F-E5).
     */
    fun check(
        draft: ConfigDraft,
        context: ValidationContext = ValidationContext(),
        passwordRequired: Boolean = false,
    ): FormCheck {
        val report = ConfigValidator.validate(validatorInput(draft), context)
        val messages = report.messages.map { FieldMessage(it.key, it.severity == ValidationSeverity.Error, it.text) }.toMutableList()
        if (passwordRequired) {
            messages += FieldMessage(
                ConfigKey.PASS, true,
                "이 SSID 의 비밀번호가 SSID 라이브러리에 없습니다. 비밀번호를 입력하거나 - (오픈 AP) 를 입력하세요.",
            )
        }
        val ip = draft.raw(ConfigKey.IP).trim()
        if (ip != DHCP && ip.isNotEmpty()) {
            val canonical = Ipv4.canonical(ip)
            if (canonical != null) {
                if (canonical == Ipv4.canonical(draft.raw(ConfigKey.GW).trim())) {
                    messages += FieldMessage(ConfigKey.IP, true, "IP 와 게이트웨이가 같습니다. 다른 주소를 입력하세요.")
                }
                if (canonical == Ipv4.canonical(draft.raw(ConfigKey.SERVER).trim())) {
                    messages += FieldMessage(ConfigKey.IP, true, "IP 와 서버 IP 가 같습니다. 다른 주소를 입력하세요.")
                }
                val last = canonical.substringAfterLast('.').toIntOrNull()
                if (last == 0 || last == 255) {
                    messages += FieldMessage(ConfigKey.IP, false, "마지막 옥텟이 $last 입니다. 일반 호스트 주소로는 쓰지 않습니다.")
                }
            }
        }
        val hasError = messages.any { it.isError }
        return FormCheck(messages, if (hasError) null else report.config)
    }

    /**
     * 장치 값과 다른 키. 비밀번호는 입력이 있으면 바꾸는 것으로 본다(장치는 값을 되읽지 않는다).
     * DNS 키는 펌웨어가 지원할 때만 센다. 검증이 통과한 상태에서는 [BridgeConfig.diff] 와 같다.
     */
    fun changedKeys(draft: ConfigDraft, device: BridgeConfig, dnsSupported: Boolean): Set<ConfigKey> =
        ConfigKey.entries.filter { key ->
            when (key) {
                ConfigKey.PASS -> draft.raw(key).isNotEmpty()
                ConfigKey.DNS, ConfigKey.DNS2 -> dnsSupported && normalized(key, draft.raw(key)) != device.canonical(key)
                else -> normalized(key, draft.raw(key)) != device.canonical(key)
            }
        }.toSet()

    /** 비교용 표준형. 장치 표기(`auto`, `off`, `dhcp`, 10진 IP)에 맞춘다. */
    internal fun normalized(key: ConfigKey, raw: String): String {
        val text = raw.trim()
        return when (key) {
            ConfigKey.DNS, ConfigKey.DNS2 -> if (text.isEmpty() || text == DraftAuto) DraftAuto else Ipv4.canonical(text) ?: text
            ConfigKey.RFID_DST -> when {
                text.isEmpty() || text == OFF -> OFF
                else -> Ipv4.canonical(text) ?: text
            }
            ConfigKey.IP -> if (text == DHCP) DHCP else Ipv4.canonical(text) ?: text
            ConfigKey.MASK, ConfigKey.GW, ConfigKey.SERVER -> Ipv4.canonical(text) ?: text
            ConfigKey.AGV, ConfigKey.SERVER_PORT, ConfigKey.LOCAL_PORT, ConfigKey.RFID_PORT,
            ConfigKey.MAIN_BAUD, ConfigKey.RFID_BAUD, ConfigKey.HB_MS -> text.toIntOrNull()?.toString() ?: text
            ConfigKey.SSID, ConfigKey.PASS -> raw
        }
    }

    private const val DraftAuto = ConfigDraft.AUTO
    private const val OFF = ConfigDraft.OFF
    private const val DHCP = ConfigDraft.DHCP
}
