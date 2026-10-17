package kr.agv.cfgmanager.core.util

/** 휴대폰이 붙은 Wi-Fi 에서 읽은 값 (Android 쪽이 채운다. 빠진 값은 null). docs/DEVELOPMENT_PLAN.md 7.4. */
data class PhoneWifiSnapshot(
    val ssid: String?,
    val ipv4: String?,
    val prefix: Int?,
    val gateway: String?,
    val dns: List<String>,
    val frequencyMhz: Int?,
)

/** 프로필 편집 화면에 채울 값. null 은 "채우지 않음(기존 값 유지)". [missing] 은 못 얻은 항목 이름. */
data class PhoneWifiFields(
    val ssid: String?,
    val mask: String?,
    val gateway: String?,
    val dns: String?,
    val dns2: String?,
    val networkBase: String?,
    val fiveGhz: Boolean,
    val missing: List<String>,
)

/** 휴대폰 네트워크 값의 순수 변환 (JVM 테스트 대상). */
object PhoneNetwork {

    /** prefix 길이 → 마스크 문자열 (24 → 255.255.255.0). 0~32 밖이면 null. */
    fun maskFromPrefix(prefix: Int): String? {
        if (prefix !in 0..32) return null
        val bits = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return listOf(bits shr 24, (bits shr 16) and 255, (bits shr 8) and 255, bits and 255).joinToString(".")
    }

    /** 주파수(MHz)가 5GHz 대역이면 true. 2.4GHz 면 false, 값이 없으면 null. */
    fun isFiveGhz(frequencyMhz: Int?): Boolean? {
        if (frequencyMhz == null || frequencyMhz <= 0) return null
        return frequencyMhz in 4900..5925
    }

    /** Android 가 주는 SSID 를 정리한다. 따옴표를 떼고, 알 수 없음(`<unknown ssid>`)이나 빈 값은 null. */
    fun cleanSsid(raw: String?): String? {
        val text = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        if (text.isEmpty() || text == "<unknown ssid>") return null
        return text
    }

    /** IP 와 마스크로 네트워크 주소를 구한다 (192.168.10.107 · 255.255.255.0 → 192.168.10.0). */
    fun networkBase(ip: String, mask: String): String? {
        val address = Ipv4.toLong(ip) ?: return null
        val m = Ipv4.toLong(mask) ?: return null
        val base = address and m
        return listOf(base shr 24, (base shr 16) and 255, (base shr 8) and 255, base and 255).joinToString(".")
    }

    /** 스냅샷을 편집 화면 값으로 바꾼다. */
    fun fieldsOf(snapshot: PhoneWifiSnapshot): PhoneWifiFields {
        val missing = mutableListOf<String>()
        val ssid = cleanSsid(snapshot.ssid)
        if (ssid == null) missing += "SSID(위치 권한과 위치 서비스 필요)"
        val mask = snapshot.prefix?.let { maskFromPrefix(it) }
        if (mask == null) missing += "마스크"
        val gateway = snapshot.gateway?.takeIf { Ipv4.isValid(it) }
        if (gateway == null) missing += "게이트웨이"
        val dnsList = snapshot.dns.filter { Ipv4.isValid(it) }
        if (dnsList.isEmpty()) missing += "DNS"
        val ipv4 = snapshot.ipv4?.takeIf { Ipv4.isValid(it) }
        val base = if (ipv4 != null && mask != null) networkBase(ipv4, mask) else null
        val five = isFiveGhz(snapshot.frequencyMhz) == true
        return PhoneWifiFields(
            ssid = ssid,
            mask = mask,
            gateway = gateway,
            dns = dnsList.getOrNull(0),
            dns2 = dnsList.getOrNull(1),
            networkBase = base,
            fiveGhz = five,
            missing = missing,
        )
    }
}
