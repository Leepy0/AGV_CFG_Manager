package kr.agv.cfgmanager.ui.config

import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.profiles.IpComputation
import kr.agv.cfgmanager.core.profiles.IpRule
import kr.agv.cfgmanager.core.profiles.IpRuleCalculator
import kr.agv.cfgmanager.core.profiles.Profile

/**
 * 선택한 프로필의 IP 규칙과, 사용자가 IP 를 직접 고쳤는지.
 * 직접 고친 뒤에는 호기 번호를 바꿔도 IP 를 다시 계산하지 않고 "규칙과 다름" 을 보인다 (F-E4).
 */
data class IpRuleState(val rule: IpRule?, val overridden: Boolean = false)

/**
 * 프로필 · 호기 번호 · SSID 라이브러리로 설정 폼을 채우는 규칙 (docs/DEVELOPMENT_PLAN.md F-E4 · F-E5 · F-P3).
 * 화면과 Android 에 의존하지 않는 순수 함수라 JVM 에서 검사한다.
 */
object AutoFill {

    /** 규칙이 호기 번호로 만든 IP. 규칙이 없거나 범위를 벗어나면 null. */
    fun ruleIp(rule: IpRule?, agv: Int?): String? {
        if (agv == null) return null
        return (rule as? IpRule.Offset)?.let { (IpRuleCalculator.compute(it, agv) as? IpComputation.Ok)?.ip }
    }

    /**
     * 라이브러리 비밀번호 → 장치에 보낼 값. null = 라이브러리에 없음, "" = 오픈 AP (wire `-`), 그 밖에는 비밀번호.
     */
    fun wirePassword(libraryPassword: String?): String? = when (libraryPassword) {
        null -> null
        "" -> "-"
        else -> libraryPassword
    }

    /**
     * 프로필 값을 초안에 채운다. 호기 번호는 [agv] 로 맞춘다.
     * 비밀번호는 라이브러리 값을 쓰고, 라이브러리에 없으면 초안의 기존 입력을 유지한다.
     */
    fun applyProfile(base: ConfigDraft?, profile: Profile, agv: Int, libraryPassword: String?): Pair<ConfigDraft, IpRuleState> {
        val pass = wirePassword(libraryPassword) ?: base?.raw(ConfigKey.PASS).orEmpty()
        val values = LinkedHashMap(profile.draft(agv, pass))
        if (profile.ipRule == IpRule.Manual) {
            values[ConfigKey.IP] = base?.raw(ConfigKey.IP).orEmpty()
        }
        val mode = when (profile.ipRule) {
            is IpRule.Offset -> IpMode.Rule
            IpRule.Dhcp -> IpMode.Dhcp
            IpRule.Manual -> IpMode.Manual
        }
        return ConfigDraft(values, mode) to IpRuleState(profile.ipRule, overridden = false)
    }

    /**
     * 호기 번호를 바꾼다. 규칙 모드이고 직접 고친 적이 없으면 IP 를 규칙값으로 다시 계산한다
     * (범위를 벗어나면 IP 를 비워 두어 검증이 알리게 한다).
     */
    fun changeAgv(draft: ConfigDraft, state: IpRuleState, agv: Int): ConfigDraft {
        val withAgv = draft.copy(values = draft.values + (ConfigKey.AGV to agv.toString()))
        if (state.rule !is IpRule.Offset || state.overridden || draft.ipMode != IpMode.Rule) return withAgv
        return withAgv.copy(values = withAgv.values + (ConfigKey.IP to (ruleIp(state.rule, agv).orEmpty())))
    }

    /** 규칙값으로 되돌린다 (IP 모드 규칙 선택, [규칙값으로]). 계산할 수 없으면 null. */
    fun useRule(draft: ConfigDraft, state: IpRuleState, agv: Int?): Pair<ConfigDraft, IpRuleState>? {
        val ip = ruleIp(state.rule, agv) ?: return null
        val next = draft.copy(values = draft.values + (ConfigKey.IP to ip), ipMode = IpMode.Rule)
        return next to state.copy(overridden = false)
    }

    /** 직접 고쳐서 규칙과 달라졌으면 규칙값, 아니면 null ("규칙과 다름" 표시용). */
    fun mismatchIp(draft: ConfigDraft, state: IpRuleState, agv: Int?): String? {
        if (state.rule !is IpRule.Offset || !state.overridden) return null
        val ip = ruleIp(state.rule, agv) ?: return null
        return if (ip == draft.raw(ConfigKey.IP).trim()) null else ip
    }

    /**
     * 비밀번호가 필요한지. 장치의 SSID 와 다른 SSID 를 넣었는데 비밀번호도 없고 라이브러리에도 없으면 필수다 (F-E5).
     * 장치가 연결돼 있지 않으면(장치 SSID 를 모르면) 필수가 아니다.
     */
    fun passwordRequired(draft: ConfigDraft, deviceSsid: String?, libraryHasSecret: Boolean): Boolean {
        val ssid = draft.raw(ConfigKey.SSID)
        return deviceSsid != null && ssid.isNotEmpty() && ssid != deviceSsid &&
            draft.raw(ConfigKey.PASS).isEmpty() && !libraryHasSecret
    }
}
