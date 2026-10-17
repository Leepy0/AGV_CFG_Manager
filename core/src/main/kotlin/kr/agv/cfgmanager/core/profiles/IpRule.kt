package kr.agv.cfgmanager.core.profiles

import kr.agv.cfgmanager.core.util.Ipv4

/**
 * 호기별 IP 규칙 (docs/DEVELOPMENT_PLAN.md 7.4, B-1).
 * - [Offset]: 기준 네트워크(예 192.168.10.0)의 마지막 옥텟 = offset + 호기 번호
 * - [Dhcp]: 고정 IP 를 보내지 않는다 (DHCP 서버에서 예약)
 * - [Manual]: 매번 직접 입력한다
 */
sealed interface IpRule {
    data class Offset(val baseNetwork: String, val offset: Int) : IpRule
    data object Dhcp : IpRule
    data object Manual : IpRule

    companion object {
        /** 계산 가능한 마지막 옥텟 범위. 0 은 네트워크 주소, 255 는 브로드캐스트라 제외한다. */
        const val HOST_MIN = 1
        const val HOST_MAX = 254
    }
}

/** 호기 번호로 IP 를 계산한 결과. */
sealed interface IpComputation {
    /** [ip] 는 점 구분 표준 표기. */
    data class Ok(val ip: String) : IpComputation
    /** 계산 결과가 호스트 범위(1~254)를 벗어난다. [lastOctet] 은 계산된 값. */
    data class OutOfRange(val lastOctet: Int) : IpComputation
    /** 기준 네트워크 주소가 잘못됐다. */
    data object BadBase : IpComputation
    /** 규칙이 호기 번호로 IP 를 만들지 않는다 (DHCP · 직접 입력). */
    data object NotComputed : IpComputation
}

object IpRuleCalculator {

    fun compute(rule: IpRule, agv: Int): IpComputation = when (rule) {
        IpRule.Dhcp, IpRule.Manual -> IpComputation.NotComputed
        is IpRule.Offset -> computeOffset(rule, agv)
    }

    private fun computeOffset(rule: IpRule.Offset, agv: Int): IpComputation {
        val octets = Ipv4.parse(rule.baseNetwork) ?: return IpComputation.BadBase
        val last = rule.offset + agv
        if (last !in IpRule.HOST_MIN..IpRule.HOST_MAX) return IpComputation.OutOfRange(last)
        return IpComputation.Ok("${octets[0]}.${octets[1]}.${octets[2]}.$last")
    }

    /** 화면 예시 문장용: "192.168.10.{offset}+{agv}" 꼴. */
    fun formula(rule: IpRule.Offset): String? {
        val octets = Ipv4.parse(rule.baseNetwork) ?: return null
        return "${octets[0]}.${octets[1]}.${octets[2]}.(${rule.offset} + 호기)"
    }
}
