package kr.agv.cfgmanager.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BlePreflightTest {

    private val scan = BlePreflight.PERMISSION_SCAN
    private val connect = BlePreflight.PERMISSION_CONNECT
    private val location = BlePreflight.PERMISSION_LOCATION

    @Test
    fun `Android 12 이상은 스캔과 연결 권한을 요구하고 위치는 요구하지 않는다`() {
        assertEquals(listOf(scan, connect), BlePreflight.requiredPermissions(31))
        assertEquals(false, BlePreflight.needsLocationService(31))
    }

    @Test
    fun `Android 11 이하는 위치 권한과 위치 서비스가 필요하다`() {
        assertEquals(listOf(location), BlePreflight.requiredPermissions(30))
        assertEquals(true, BlePreflight.needsLocationService(26))
    }

    @Test
    fun `권한이 빠지면 빠진 목록을 돌려준다`() {
        val result = BlePreflight.check(sdk = 34, granted = setOf(scan), bluetoothOn = true, locationOn = false)
        assertIs<Preflight.NeedPermission>(result)
        assertEquals(listOf(connect), result.permissions)
    }

    @Test
    fun `권한이 있어도 블루투스가 꺼져 있으면 켜라고 한다`() {
        val result = BlePreflight.check(sdk = 34, granted = setOf(scan, connect), bluetoothOn = false, locationOn = true)
        assertEquals(Preflight.BluetoothOff, result)
    }

    @Test
    fun `Android 11 이하에서 위치 서비스가 꺼져 있으면 위치 설정을 안내한다`() {
        val off = BlePreflight.check(sdk = 30, granted = setOf(location), bluetoothOn = true, locationOn = false)
        assertEquals(Preflight.LocationOff, off)
        val on = BlePreflight.check(sdk = 30, granted = setOf(location), bluetoothOn = true, locationOn = true)
        assertEquals(Preflight.Ready, on)
    }

    @Test
    fun `Android 12 이상은 위치 서비스가 꺼져 있어도 준비가 된다`() {
        val result = BlePreflight.check(sdk = 33, granted = setOf(scan, connect), bluetoothOn = true, locationOn = false)
        assertEquals(Preflight.Ready, result)
    }
}
