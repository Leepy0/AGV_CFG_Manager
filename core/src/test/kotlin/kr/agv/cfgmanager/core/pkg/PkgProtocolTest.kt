package kr.agv.cfgmanager.core.pkg

import kr.agv.cfgmanager.core.bridge.BridgeCommands
import java.util.Base64
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PkgProtocolTest {

    private val sha = "a".repeat(64)

    private fun slot(i: Int, st: String, seq: Long) =
        """{"i":$i,"st":"$st","id":"0123456789abcdef","ver":"1.2.3","size":123,"seq":$seq,"job":0,"sha256":"$sha"}"""

    private fun pLine(id: String, off: Long, data: ByteArray, crcOverride: String? = null, n: Int = data.size): String {
        val crc = CRC32().apply { update(data) }.value
        val crcText = crcOverride ?: "%08x".format(crc)
        return "P $id $off $n $crcText ${Base64.getEncoder().encodeToString(data)}"
    }

    @Test
    fun `명령 줄과 대량 읽기 판별`() {
        assertEquals("dsp pkg json", BridgeCommands.pkgJson())
        assertEquals("dsp pkg read 0 0 512", BridgeCommands.pkgRead(0, 0, 512))
        assertTrue(BridgeCommands.isBulkRead("dsp pkg read 0 0 512"))
        assertFalse(BridgeCommands.isBulkRead("dsp pkg json"))
        assertFalse(BridgeCommands.isBulkRead("dsp"))
    }

    @Test
    fun `슬롯 둘을 읽고 빈 슬롯은 뺀다`() {
        val text = """{"slots":[${slot(0, "APPLIED", 2)},{"i":1,"st":"empty"},${slot(2, "OLD", 1)}]}""" + "\n"
        val ok = assertIs<PkgList.Ok>(parsePkgList(text))
        assertEquals(listOf(0, 2), ok.slots.map { it.index })
        assertEquals(PkgState.Applied, ok.slots[0].state)
        assertEquals(PkgState.Old, ok.slots[1].state)
        assertEquals(PkgSlot(0, PkgState.Applied, "0123456789abcdef", "1.2.3", 123, 2, sha), ok.slots[0])
    }

    @Test
    fun `err 값은 상태로 바뀌고 모르는 값은 미지원`() {
        assertEquals(PkgList.NoPartition, parsePkgList("""{"err":"nopart"}"""))
        assertEquals(PkgList.Busy, parsePkgList("""{"err":"busy"}"""))
        assertEquals(PkgList.Unsupported, parsePkgList("""{"err":"local_only"}"""))
        assertEquals(PkgList.Unsupported, parsePkgList("""{"err":"zzz"}"""))
    }

    @Test
    fun `구버전 텍스트와 깨진 JSON 은 미지원`() {
        assertEquals(PkgList.Unsupported, parsePkgList("pkg slot0: empty\npkg slot1: empty\n"))
        assertEquals(PkgList.Unsupported, parsePkgList("dsp : app=1.0.3 boot=1.1.0 state=idle"))
        assertEquals(PkgList.Unsupported, parsePkgList("""{"slots":[{"i":0"""))
        assertEquals(PkgList.Unsupported, parsePkgList("""{"slots":5}"""))
        assertEquals(PkgList.Unsupported, parsePkgList(""))
    }

    @Test
    fun `알 수 없는 슬롯 상태는 목록에서 뺀다`() {
        val ok = assertIs<PkgList.Ok>(parsePkgList("""{"slots":[${slot(0, "WEIRD", 1)},${slot(1, "STAGED", 3)}]}"""))
        assertEquals(listOf(1), ok.slots.map { it.index })
    }

    @Test
    fun `기본 슬롯은 APPLIED 우선이고 없으면 seq 최대 STAGED`() {
        fun s(i: Int, st: PkgState, seq: Long) = PkgSlot(i, st, "id", "1", 1, seq, sha)
        assertEquals(1, defaultPkgSlot(listOf(s(0, PkgState.Staged, 9), s(1, PkgState.Applied, 1)))?.index)
        assertEquals(2, defaultPkgSlot(listOf(s(0, PkgState.Staged, 3), s(2, PkgState.Staged, 5), s(1, PkgState.Old, 9)))?.index)
        assertNull(defaultPkgSlot(listOf(s(0, PkgState.Old, 9))))
        assertNull(defaultPkgSlot(emptyList()))
    }

    @Test
    fun `정상 청크를 읽는다`() {
        val data = ByteArray(100) { it.toByte() }
        val c = assertIs<PkgChunk.Data>(parsePkgChunk(pLine("0123456789abcdef", 512, data) + "\n"))
        assertEquals("0123456789abcdef", c.id)
        assertEquals(512L, c.off)
        assertContentEquals(data, c.bytes)
    }

    @Test
    fun `CRC 나 길이가 다르면 Bad`() {
        val data = ByteArray(10) { 7 }
        assertEquals(PkgChunk.Bad, parsePkgChunk(pLine("0123456789abcdef", 0, data, crcOverride = "00000000")))
        assertEquals(PkgChunk.Bad, parsePkgChunk(pLine("0123456789abcdef", 0, data, n = 11)))
        assertEquals(PkgChunk.Bad, parsePkgChunk("P 0123456789abcdef 0 10"))
        assertEquals(PkgChunk.Bad, parsePkgChunk("P id 0 3 00000000 !!!"))
        assertEquals(PkgChunk.Bad, parsePkgChunk(""))
    }

    @Test
    fun `앞뒤에 다른 줄이 있어도 P 줄을 찾는다`() {
        val data = ByteArray(20) { (it * 3).toByte() }
        val text = "I (123) wifi: noise\r\n${pLine("0123456789abcdef", 40, data)}\r\ndsp>\r\n"
        val c = assertIs<PkgChunk.Data>(parsePkgChunk(text))
        assertEquals(40L, c.off)
        assertContentEquals(data, c.bytes)
    }

    @Test
    fun `E 줄은 거절 코드`() {
        assertEquals(PkgChunk.Refused("busy"), parsePkgChunk("E busy\n"))
        assertEquals(PkgChunk.Refused("range"), parsePkgChunk("log\nE range\n"))
    }
}
