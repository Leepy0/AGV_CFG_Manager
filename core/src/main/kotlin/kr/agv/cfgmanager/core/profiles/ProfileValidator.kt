package kr.agv.cfgmanager.core.profiles

import kr.agv.cfgmanager.core.util.Ipv4
import kr.agv.cfgmanager.core.util.utf8Length

/** 프로필 편집 화면의 항목. 문구를 항목 아래에 붙이는 데 쓴다. */
enum class ProfileField { Name, Ssid, IpRule, Mask, Gw, Dns, Dns2, Server, ServerPort, LocalPort, RfidDst, RfidPort, MainBaud, RfidBaud, HbMs, BlePassword }

data class ProfileIssue(val field: ProfileField, val isError: Boolean, val text: String)

/**
 * 프로필 값 검사 (편집 화면 저장 전). 브리지 검증 규칙 중 프로필에 있는 값만 본다.
 * 호기 번호는 프로필에 없으므로 IP 규칙은 1~99 전체가 1~254 안에 드는지로 본다.
 */
object ProfileValidator {

    fun validate(profile: Profile): List<ProfileIssue> {
        val issues = mutableListOf<ProfileIssue>()
        fun error(field: ProfileField, text: String) { issues += ProfileIssue(field, true, text) }
        fun warn(field: ProfileField, text: String) { issues += ProfileIssue(field, false, text) }

        if (profile.name.isBlank()) error(ProfileField.Name, "프로필 이름을 입력하세요.")

        val ssidBytes = profile.ssid.utf8Length()
        when {
            ssidBytes > 32 -> error(ProfileField.Ssid, "SSID 는 32바이트 이하입니다 (현재 ${ssidBytes}바이트).")
            profile.ssid.isEmpty() -> warn(ProfileField.Ssid, "SSID 가 비어 있으면 적용할 때 직접 입력해야 합니다.")
        }

        when (val rule = profile.ipRule) {
            is IpRule.Offset -> {
                if (Ipv4.parse(rule.baseNetwork) == null) {
                    error(ProfileField.IpRule, "기준 네트워크 주소가 올바르지 않습니다.")
                } else {
                    val low = IpRuleCalculator.compute(rule, 1)
                    val high = IpRuleCalculator.compute(rule, 99)
                    if (low is IpComputation.OutOfRange || high is IpComputation.OutOfRange) {
                        warn(ProfileField.IpRule, "오프셋 ${rule.offset} 이면 일부 호기(1~99)의 IP 가 1~254 를 벗어납니다.")
                    }
                }
            }
            IpRule.Dhcp, IpRule.Manual -> Unit
        }

        when (Ipv4.isContiguousMask(profile.mask)) {
            null -> error(ProfileField.Mask, "마스크 형식이 올바르지 않습니다 (예: 255.255.255.0).")
            false -> warn(ProfileField.Mask, "연속되지 않은 마스크입니다. 의도한 값인지 확인하세요.")
            true -> if (profile.mask == "0.0.0.0") error(ProfileField.Mask, "마스크는 0.0.0.0 일 수 없습니다.")
        }
        if (!Ipv4.isValid(profile.gw)) error(ProfileField.Gw, "게이트웨이 IP 형식이 올바르지 않습니다.")
        dnsCheck(profile.dns, ProfileField.Dns, ::error)
        dnsCheck(profile.dns2, ProfileField.Dns2, ::error)
        if (!Ipv4.isValid(profile.server) || profile.server == "0.0.0.0") {
            error(ProfileField.Server, "서버 IP 형식이 올바르지 않습니다.")
        }
        portCheck(profile.serverPort, ProfileField.ServerPort, ::error)
        portCheck(profile.localPort, ProfileField.LocalPort, ::error)
        if (profile.rfidDst != "off" && !Ipv4.isValid(profile.rfidDst)) {
            error(ProfileField.RfidDst, "RFID 송신 IP 는 IP 주소 또는 off 입니다.")
        }
        portCheck(profile.rfidPort, ProfileField.RfidPort, ::error)
        baudCheck(profile.mainBaud, ProfileField.MainBaud, ::error)
        baudCheck(profile.rfidBaud, ProfileField.RfidBaud, ::error)
        if (profile.hbMs != 0 && profile.hbMs !in 100..2000) {
            error(ProfileField.HbMs, "heartbeat 주기는 0(끔) 또는 100~2000 ms 입니다.")
        }
        if (profile.blePassword.isEmpty()) {
            warn(ProfileField.BlePassword, "BLE 비밀번호가 없습니다. 보드에 연결할 때 입력해야 합니다.")
        }
        return issues
    }

    fun hasErrors(issues: List<ProfileIssue>): Boolean = issues.any { it.isError }

    private fun dnsCheck(value: String, field: ProfileField, error: (ProfileField, String) -> Unit) {
        if (value == "auto") return
        if (!Ipv4.isValid(value) || value == "0.0.0.0") error(field, "DNS 는 IP 주소 또는 auto 입니다.")
    }

    private fun portCheck(value: Int, field: ProfileField, error: (ProfileField, String) -> Unit) {
        if (value !in 1..65535) error(field, "포트는 1~65535 입니다.")
    }

    private fun baudCheck(value: Int, field: ProfileField, error: (ProfileField, String) -> Unit) {
        if (value !in 1200..1000000) error(field, "보드레이트는 1200~1000000 입니다.")
    }
}
