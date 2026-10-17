package kr.agv.cfgmanager.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kr.agv.cfgmanager.core.util.PhoneWifiSnapshot
import java.net.Inet4Address

/**
 * 휴대폰이 붙은 Wi-Fi 의 값을 읽는다 (docs/DEVELOPMENT_PLAN.md 7.4). 순수 변환은 `:core` 의 PhoneNetwork 가 맡는다.
 * Wi-Fi 가 아니면 null. SSID 는 위치 권한과 위치 서비스가 있어야 나오며, 못 얻으면 null 로 둔다.
 */
object PhoneWifi {

    fun read(context: Context): PhoneWifiSnapshot? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val link = connectivity.getLinkProperties(network)
        val address = link?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val gateway = link?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val dns = link?.dnsServers?.filterIsInstance<Inet4Address>()?.mapNotNull { it.hostAddress } .orEmpty()
        val info = wifiInfo(context, caps)
        return PhoneWifiSnapshot(
            ssid = info?.ssid,
            ipv4 = address?.address?.hostAddress,
            prefix = address?.prefixLength,
            gateway = gateway,
            dns = dns,
            frequencyMhz = info?.frequency,
        )
    }

    @Suppress("DEPRECATION")
    private fun wifiInfo(context: Context, caps: NetworkCapabilities): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (caps.transportInfo as? WifiInfo)?.let { return it }
        }
        return context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
    }
}
