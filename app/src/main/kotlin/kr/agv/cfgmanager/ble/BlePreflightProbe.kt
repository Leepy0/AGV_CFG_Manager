package kr.agv.cfgmanager.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/** 현재 휴대폰 상태를 [BlePreflight] 규칙에 넣어 본다. */
class BlePreflightProbe(private val context: Context, private val adapter: BluetoothAdapter?) {

    fun check(): Preflight {
        val sdk = Build.VERSION.SDK_INT
        val granted = BlePreflight.requiredPermissions(sdk)
            .filter { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            .toSet()
        val bluetoothOn = adapter?.isEnabled == true
        val locationManager = context.getSystemService(LocationManager::class.java)
        val locationOn = when {
            locationManager == null -> false
            sdk >= Build.VERSION_CODES.P -> locationManager.isLocationEnabled
            else -> locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
        return BlePreflight.check(sdk, granted, bluetoothOn, locationOn)
    }
}
