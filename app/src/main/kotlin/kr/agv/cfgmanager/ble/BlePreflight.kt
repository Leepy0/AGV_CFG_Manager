package kr.agv.cfgmanager.ble

/** 스캔 전 준비 상태 (docs/DEVELOPMENT_PLAN.md 8.1 · 9.1). */
sealed interface Preflight {
    data object Ready : Preflight
    data class NeedPermission(val permissions: List<String>) : Preflight
    data object BluetoothOff : Preflight
    data object LocationOff : Preflight
}

/**
 * 권한 매트릭스 (8.1). 순수 함수라 JVM 에서 검증한다.
 * Android 12+(API 31): BLUETOOTH_SCAN · BLUETOOTH_CONNECT. 11 이하: 위치 권한 + 위치 서비스 켜짐.
 */
object BlePreflight {
    const val PERMISSION_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val PERMISSION_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val PERMISSION_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val SDK_SCAN_SPLIT = 31

    fun requiredPermissions(sdk: Int): List<String> =
        if (sdk >= SDK_SCAN_SPLIT) listOf(PERMISSION_SCAN, PERMISSION_CONNECT) else listOf(PERMISSION_LOCATION)

    /** 11 이하는 위치 서비스가 꺼져 있으면 스캔 결과가 비어 있다. */
    fun needsLocationService(sdk: Int): Boolean = sdk < SDK_SCAN_SPLIT

    fun check(sdk: Int, granted: Set<String>, bluetoothOn: Boolean, locationOn: Boolean): Preflight {
        val missing = requiredPermissions(sdk).filter { it !in granted }
        if (missing.isNotEmpty()) return Preflight.NeedPermission(missing)
        if (!bluetoothOn) return Preflight.BluetoothOff
        if (needsLocationService(sdk) && !locationOn) return Preflight.LocationOff
        return Preflight.Ready
    }
}
