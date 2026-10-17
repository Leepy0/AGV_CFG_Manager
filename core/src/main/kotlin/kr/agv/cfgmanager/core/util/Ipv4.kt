package kr.agv.cfgmanager.core.util

/**
 * IPv4 주소 문자열 검사. 설정 검증(ConfigValidator)과 IP 규칙(IpRule)이 공통으로 쓴다.
 *
 * 규칙: 점으로 나눈 네 칸, 각 칸은 0~255 의 10진수, 앞자리 0 금지("192.168.010.1" 거부).
 * 앞자리 0 은 앱 정책이다. 브리지 `set ip` 는 sscanf 로 받아 앞자리 0 을 허용하지만(10진 해석),
 * 입력 실수를 막으려고 앱은 거부한다.
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

    /** 올바른 주소면 표준 표기(옥텟 10진, 점 구분), 아니면 null. */
    fun canonical(text: String): String? = parse(text)?.joinToString(".")

    /** 주소를 32비트 값(네트워크 순서를 큰 수로 본 값)으로. */
    fun toLong(text: String): Long? = parse(text)?.let { o ->
        (o[0].toLong() shl 24) or (o[1].toLong() shl 16) or (o[2].toLong() shl 8) or o[3].toLong()
    }

    /** 두 주소가 같은 서브넷인지. 주소나 마스크가 잘못되면 null. */
    fun sameSubnet(a: String, b: String, mask: String): Boolean? {
        val x = toLong(a) ?: return null
        val y = toLong(b) ?: return null
        val m = toLong(mask) ?: return null
        return (x and m) == (y and m)
    }

    /** 마스크가 1 로 시작해 0 으로 끝나는 연속형인지(예: 255.255.255.0). 잘못되면 null. */
    fun isContiguousMask(text: String): Boolean? {
        val value = toLong(text) ?: return null
        val inverted = value.inv() and 0xFFFFFFFFL
        return (inverted and (inverted + 1)) == 0L
    }
}
