package kr.agv.cfgmanager.ui.profiles

import kr.agv.cfgmanager.core.profiles.IpComputation
import kr.agv.cfgmanager.core.profiles.IpRule
import kr.agv.cfgmanager.core.profiles.IpRuleCalculator
import kr.agv.cfgmanager.core.profiles.Profile
import kr.agv.cfgmanager.core.profiles.ProfileField
import kr.agv.cfgmanager.core.profiles.ProfileIssue
import kr.agv.cfgmanager.core.profiles.ProfileValidator
import kr.agv.cfgmanager.core.util.PhoneWifiFields

/** 프로필 편집 화면의 IP 규칙 모드. */
enum class RuleMode { Offset, Dhcp, Manual }

/**
 * 프로필 편집 화면의 입력 원문. 숫자 칸은 문자열로 두고 저장할 때 바꾼다 (잘못된 숫자는 검사 문구로 알린다).
 */
data class ProfileForm(
    val id: String,
    val name: String,
    val notes: String,
    val createdAt: String,
    val lastUsedAt: String?,
    val ssid: String,
    val ruleMode: RuleMode,
    val baseNetwork: String,
    val offset: String,
    val mask: String,
    val gw: String,
    val dns: String,
    val dns2: String,
    val server: String,
    val serverPort: String,
    val localPort: String,
    val rfidDst: String,
    val rfidPort: String,
    val mainBaud: String,
    val rfidBaud: String,
    val hbMs: String,
    val blePassword: String,
) {
    /** 숫자로 바꾸지 못한 칸의 오류. 항목마다 어느 칸인지 붙인다. 비어 있으면 빈 목록. */
    fun numericErrors(): List<ProfileIssue> {
        val errors = mutableListOf<ProfileIssue>()
        fun check(field: ProfileField, label: String, text: String) {
            if (text.toIntOrNull() == null) errors += ProfileIssue(field, true, "$label 는 숫자로 입력하세요.")
        }
        if (ruleMode == RuleMode.Offset) check(ProfileField.IpRule, "오프셋", offset)
        check(ProfileField.ServerPort, "서버 포트", serverPort)
        check(ProfileField.LocalPort, "수신 포트", localPort)
        check(ProfileField.RfidPort, "RFID 포트", rfidPort)
        check(ProfileField.MainBaud, "메인보드 보드레이트", mainBaud)
        check(ProfileField.RfidBaud, "RFID 보드레이트", rfidBaud)
        check(ProfileField.HbMs, "heartbeat 주기", hbMs)
        return errors
    }

    /** 숫자 오류가 없을 때만 프로필로 만든다. 값 검사는 [issues] 가 맡는다. */
    fun toProfile(nowIso: String): Profile = Profile(
        id = id,
        name = name.trim(),
        notes = notes.trim(),
        createdAt = createdAt,
        updatedAt = nowIso,
        lastUsedAt = lastUsedAt,
        ssid = ssid,
        ipRule = when (ruleMode) {
            RuleMode.Offset -> IpRule.Offset(baseNetwork.trim(), offset.toIntOrNull() ?: 0)
            RuleMode.Dhcp -> IpRule.Dhcp
            RuleMode.Manual -> IpRule.Manual
        },
        mask = mask.trim(),
        gw = gw.trim(),
        dns = dns.trim().ifEmpty { "auto" },
        dns2 = dns2.trim().ifEmpty { "auto" },
        server = server.trim(),
        serverPort = serverPort.toIntOrNull() ?: -1,
        localPort = localPort.toIntOrNull() ?: -1,
        rfidDst = rfidDst.trim(),
        rfidPort = rfidPort.toIntOrNull() ?: -1,
        mainBaud = mainBaud.toIntOrNull() ?: -1,
        rfidBaud = rfidBaud.toIntOrNull() ?: -1,
        hbMs = hbMs.toIntOrNull() ?: -1,
        blePassword = blePassword,
    )

    /** 숫자 칸 안내 + 프로필 값 검사. 오류가 하나라도 있으면 저장할 수 없다. */
    fun issues(nowIso: String): List<ProfileIssue> {
        val numeric = numericErrors()
        if (numeric.isNotEmpty()) return numeric
        return ProfileValidator.validate(toProfile(nowIso))
    }

    /** 예시 문장용 계산: 호기 [agv] 의 IP. 규칙이 아니거나 범위를 벗어나면 null. */
    fun exampleIp(agv: Int): String? {
        if (ruleMode != RuleMode.Offset) return null
        val rule = IpRule.Offset(baseNetwork.trim(), offset.toIntOrNull() ?: return null)
        return (IpRuleCalculator.compute(rule, agv) as? IpComputation.Ok)?.ip
    }

    /** 휴대폰 Wi-Fi 에서 읽은 값을 채운다. null 인 값은 기존 입력을 그대로 둔다 (7.4). */
    fun withPhoneWifi(fields: PhoneWifiFields): ProfileForm = copy(
        ssid = fields.ssid ?: ssid,
        mask = fields.mask ?: mask,
        gw = fields.gateway ?: gw,
        dns = fields.dns ?: dns,
        dns2 = fields.dns2 ?: dns2,
        baseNetwork = fields.networkBase ?: baseNetwork,
        ruleMode = if (fields.networkBase != null) RuleMode.Offset else ruleMode,
    )

    companion object {
        fun of(profile: Profile): ProfileForm {
            val rule = profile.ipRule
            return ProfileForm(
                id = profile.id,
                name = profile.name,
                notes = profile.notes,
                createdAt = profile.createdAt,
                lastUsedAt = profile.lastUsedAt,
                ssid = profile.ssid,
                ruleMode = when (rule) {
                    is IpRule.Offset -> RuleMode.Offset
                    IpRule.Dhcp -> RuleMode.Dhcp
                    IpRule.Manual -> RuleMode.Manual
                },
                baseNetwork = (rule as? IpRule.Offset)?.baseNetwork ?: "192.168.10.0",
                offset = (rule as? IpRule.Offset)?.offset?.toString() ?: "100",
                mask = profile.mask,
                gw = profile.gw,
                dns = profile.dns,
                dns2 = profile.dns2,
                server = profile.server,
                serverPort = profile.serverPort.toString(),
                localPort = profile.localPort.toString(),
                rfidDst = profile.rfidDst,
                rfidPort = profile.rfidPort.toString(),
                mainBaud = profile.mainBaud.toString(),
                rfidBaud = profile.rfidBaud.toString(),
                hbMs = profile.hbMs.toString(),
                blePassword = profile.blePassword,
            )
        }
    }
}
