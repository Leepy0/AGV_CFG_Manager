package kr.agv.cfgmanager.core.history

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryCodecTest {

    private val ok = HistoryEntry(
        at = "2026-10-17T10:15:32Z", profileId = "hq-f1", profileName = "본사 1공장", agv = 7,
        bleAddress = "AA:BB:CC:DD:EE:F8", mac = "a1:b2:c3:d4:a1:b2", deviceName = "AGVBR-07-E5F6",
        ip = "192.168.10.107", ssid = "AGV_WIFI", fw = "0.5.5", channel = "ble", action = "apply",
        result = "ok", rssi = -55, note = "",
    )

    private val failed = ok.copy(
        agv = 8, mac = null, ip = "192.168.10.108", fw = "0.5.2", result = "wifi_down:201",
        rssi = null, note = "AP 안 보임, \"현장\" 확인, 필요",
    )

    @Test
    fun `한 줄 인코딩은 줄바꿈이 없고 왕복하면 같다`() {
        val line = HistoryCodec.encodeLine(failed)
        assertFalse(line.contains('\n'))
        assertEquals(failed, HistoryCodec.decodeLine(line))
    }

    @Test
    fun `빈 값은 null 로 남는다`() {
        val line = HistoryCodec.encodeLine(failed)
        val decoded = HistoryCodec.decodeLine(line)!!
        assertNull(decoded.mac)
        assertNull(decoded.rssi)
    }

    @Test
    fun `모르는 필드는 보존된다`() {
        val extra = JsonObject(mapOf("future" to JsonPrimitive("kept")))
        val line = HistoryCodec.encodeLine(ok.copy(extras = extra))
        assertTrue(line.contains("\"future\":\"kept\""))
        assertEquals(extra, HistoryCodec.decodeLine(line)!!.extras)
    }

    @Test
    fun `깨진 줄과 빈 줄은 건너뛴다`() {
        val text = HistoryCodec.encodeLine(ok) + "\n\nnot json\n" + HistoryCodec.encodeLine(failed) + "\n"
        assertEquals(listOf(ok, failed), HistoryCodec.decodeLines(text))
        assertNull(HistoryCodec.decodeLine("{\"at\":\"x\"}"))
    }

    @Test
    fun `여러 건은 줄마다 한 건씩 끝 줄바꿈을 붙인다`() {
        val text = HistoryCodec.encodeLines(listOf(ok, failed))
        assertEquals(2, text.lines().count { it.isNotEmpty() })
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun `CSV 는 헤더와 CRLF 를 쓰고 쉼표 따옴표 줄바꿈을 감싼다`() {
        val csv = HistoryCodec.encodeCsv(listOf(failed), withBom = false)
        val lines = csv.split("\r\n")
        assertEquals(
            "at,profileName,agv,deviceName,ip,ssid,fw,mac,channel,action,result,rssi,note",
            lines[0],
        )
        assertTrue(lines[1].endsWith(",\"AP 안 보임, \"\"현장\"\" 확인, 필요\""), lines[1])
        assertTrue(lines[1].contains("wifi_down:201"))
    }

    @Test
    fun `CSV 는 기본으로 BOM 을 붙여 엑셀에서 한글이 깨지지 않게 한다`() {
        assertTrue(HistoryCodec.encodeCsv(emptyList()).startsWith("﻿"))
        assertFalse(HistoryCodec.encodeCsv(emptyList(), withBom = false).startsWith("﻿"))
    }
}
