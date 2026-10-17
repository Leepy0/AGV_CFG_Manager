package kr.agv.cfgmanager.core.util

/**
 * IPv4 주소 문자열 검사. 설정 검증(ConfigValidator)과 IP 규칙(IpRule)이 공통으로 쓴다.
 *
 * 규칙: 점으로 나눈 네 칸, 각 칸은 0~255 의 10진수, 앞자리 0 금지("192.168.010.1" 거부).
 */
object Ipv4 {

    /** 주소 문자열이면 네 옥텟 값을, 아니면 null 을 돌려준다. */
    fun parse(text: String): IntArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for ((index, part) in parts.withIndex()) {
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
            if (part.length > 1 && part[0] == '0') return null
            val value = part.toInt()
            if (value > 255) return null
            octets[index] = value
        }
        return octets
    }

    fun isValid(text: String): Boolean = parse(text) != null
}
