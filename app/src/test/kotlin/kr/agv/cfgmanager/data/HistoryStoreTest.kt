package kr.agv.cfgmanager.data

import kr.agv.cfgmanager.core.history.HistoryEntry
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryStoreTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("history-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun entry(agv: Int, result: String) = HistoryEntry(
        at = "2026-10-17T10:15:32Z",
        profileId = null,
        profileName = null,
        agv = agv,
        bleAddress = "AA:BB:CC:DD:EE:F8",
        mac = "a1:b2:c3:d4:a1:b2",
        deviceName = "AGVBR-0$agv-A1B2",
        ip = "192.168.10.10$agv",
        ssid = "AGV_WIFI",
        fw = "0.5.5",
        channel = "ble",
        action = "apply",
        result = result,
        rssi = -55,
        note = "",
    )

    @Test
    fun `없는 파일은 빈 목록이다`() {
        val store = HistoryStore(File(dir, "history.jsonl"))
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun `추가한 건을 순서대로 읽고 파일은 줄 단위로 늘어난다`() {
        val file = File(dir, "history.jsonl")
        val store = HistoryStore(file)
        store.append(entry(7, "ok"))
        store.append(entry(8, "wifi_down:201"))

        val read = store.readAll()
        assertEquals(listOf(7, 8), read.map { it.agv })
        assertEquals("wifi_down:201", read[1].result)
        assertEquals(2, file.readLines().size)
    }

    @Test
    fun `다른 저장소 인스턴스에서도 이어서 읽는다`() {
        val file = File(dir, "history.jsonl")
        HistoryStore(file).append(entry(7, "ok"))
        assertEquals(1, HistoryStore(file).readAll().size)
    }

    @Test
    fun `깨진 줄은 건너뛴다`() {
        val file = File(dir, "history.jsonl")
        HistoryStore(file).append(entry(7, "ok"))
        file.appendText("이건 JSON 이 아님\n")
        HistoryStore(file).append(entry(9, "ok"))
        assertEquals(listOf(7, 9), HistoryStore(file).readAll().map { it.agv })
    }
}
