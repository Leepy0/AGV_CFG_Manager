package kr.agv.cfgmanager.data

import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.bridge.BridgeConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestorePointStoreTest {

    private fun config(agv: Int, ip: String? = "192.168.10.107") = BridgeConfig(
        agv = agv, ssid = "test-ap", pass = null, passLength = 10, ip = ip, mask = "255.255.255.0",
        gw = "192.168.10.1", dns = null, dns2 = null, server = "192.168.10.10", serverPort = 5000,
        localPort = 5001, rfidDst = null, rfidPort = 5002, mainBaud = 115200, rfidBaud = 9600, hbMs = 1000,
    )

    private fun newFile() = Files.createTempDirectory("restore").resolve("restore_points.json").toFile()

    @Test
    fun `저장한 값을 평문 비밀번호까지 그대로 읽는다`() = runTest {
        val store = RestorePointStore(newFile())
        val point = RestorePoint("AA:01", "2026-10-18T09:00:00Z", "AGVBR-07", config(7), "secret-pass-1")
        store.save(point)
        assertEquals(point, store.find("AA:01"))
    }

    @Test
    fun `장치마다 마지막 1건만 남고 비밀번호를 모르면 null 이다`() = runTest {
        val store = RestorePointStore(newFile())
        store.save(RestorePoint("AA:01", "t1", null, config(7), "old-pass-123"))
        store.save(RestorePoint("AA:01", "t2", null, config(8, ip = null), null))
        val found = store.find("AA:01")
        assertEquals(8, found?.config?.agv)
        assertNull(found?.config?.ip)
        assertNull(found?.password)
        assertNull(store.find("BB:02"))
    }

    @Test
    fun `깨진 파일은 빈 목록으로 본다`() = runTest {
        val file = newFile().also { it.writeText("{not json") }
        assertNull(RestorePointStore(file).find("AA:01"))
    }
}
