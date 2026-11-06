package kr.agv.cfgmanager.core.pkg

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val MAIN_C = "// 한글 주석\nint main(void) { return 0; }\n".toByteArray()
private val TAG_H = "#define TAG 1\n".toByteArray()

class EmbeddedSourceTest {
    private lateinit var root: File
    private lateinit var out: File
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("srcembed").toFile()
        out = File(root, "AGV_Main.out")
        dir = File(root, "src")
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun rejected(bytes: ByteArray) {
        out.writeBytes(bytes)
        val e = assertFailsWith<PkgFormatException> { EmbeddedSource.extract(out, dir) }
        assertTrue("내장 소스 손상" in e.message.orEmpty(), "거부 이유가 다름: ${e.message}")
        assertEquals(emptyList(), dir.list().orEmpty().toList(), "거부 뒤 dir 이 비어 있어야 함")
    }

    private fun srcSection(blob: ByteArray, style: NameStyle = NameStyle.ZERO_OFFSET) =
        coffOf(Section(".text", ByteArray(4)), Section(".srcfiles", blob, style))

    @Test
    fun `두 파일을 바이트 그대로 복원한다`() {
        out.writeBytes(srcCoffOf("main.c" to MAIN_C, "tag.h" to TAG_H))

        val files = EmbeddedSource.extract(out, dir)

        assertEquals(listOf(File(dir, "main.c"), File(dir, "tag.h")), files)
        assertContentEquals(MAIN_C, files[0].readBytes())
        assertContentEquals(TAG_H, files[1].readBytes())
        assertEquals(setOf("main.c", "tag.h"), dir.list().orEmpty().toSet())
    }

    @Test
    fun `실제 빌드 표본에서 세 파일을 꺼낸다`() {
        val sample = javaClass.getResourceAsStream("/sample_embedded.out")!!.use { it.readBytes() }
        out.writeBytes(sample)

        val files = EmbeddedSource.extract(out, dir)

        assertEquals(
            listOf("run_process_DA.c" to 258_000L, "tag_DA.c" to 49L, "test.cmd" to 320L),
            files.map { it.name to it.length() },
        )
    }

    @Test
    fun `섹션이 없으면 빈 목록이고 dir 을 만들지 않는다`() {
        out.writeBytes(coffOf(Section(".text", ByteArray(8)), Section(".cinit", ByteArray(4))))

        assertEquals(emptyList(), EmbeddedSource.extract(out, dir))
        assertFalse(dir.exists())
    }

    @Test
    fun `COFF 가 아니거나 짧은 파일은 섹션 없음이다`() {
        for (bytes in listOf(ByteArray(5000) { (it % 251).toByte() }, ByteArray(0), ByteArray(10) { 0xC2.toByte() })) {
            out.writeBytes(bytes)
            assertEquals(emptyList(), EmbeddedSource.extract(out, dir))
        }
    }

    @Test
    fun `이전 파일은 지운다`() {
        File(dir, "old").mkdirs()
        File(dir, "old/x.c").writeText("old")
        File(dir, "main.c").writeText("old")
        out.writeBytes(coffOf(Section(".text", ByteArray(8))))
        EmbeddedSource.extract(out, dir)
        assertFalse(dir.exists())

        File(dir, "old").mkdirs()
        out.writeBytes(srcCoffOf("main.c" to MAIN_C))
        EmbeddedSource.extract(out, dir)
        assertEquals(setOf("main.c"), dir.list().orEmpty().toSet())
    }

    @Test
    fun `문자열 표의 슬래시 이름도 읽는다`() {
        out.writeBytes(srcSection(srcBlobOf(rawOf("main.c" to MAIN_C)), NameStyle.SLASH))

        assertEquals(listOf(File(dir, "main.c")), EmbeddedSource.extract(out, dir))
    }

    @Test
    fun `홀수 길이 섹션의 끝 패딩은 무시한다`() {
        val blob = srcBlobOf(rawOf("main.c" to MAIN_C, "tag.h" to TAG_H))
        val odd = if (blob.size % 2 == 1) blob else srcBlobOf(rawOf("main.c" to MAIN_C, "tag.hh" to TAG_H))
        assertEquals(1, odd.size % 2, "시험 준비 오류: 홀수 길이가 아님")
        out.writeBytes(srcSection(odd))

        assertEquals(2, EmbeddedSource.extract(out, dir).size)
    }

    @Test
    fun `빈 목록도 정상이다`() {
        out.writeBytes(srcSection(srcBlobOf(ByteArray(0))))

        assertEquals(emptyList(), EmbeddedSource.extract(out, dir))
    }

    @Test
    fun `매직이 다르면 거부한다`() {
        rejected(srcSection(srcBlobOf(rawOf("main.c" to MAIN_C), magic = "AGVSRC2\u0000")))
    }

    @Test
    fun `풀린 길이가 선언보다 길면 거부한다`() {
        val raw = rawOf("main.c" to MAIN_C)
        rejected(srcSection(srcBlobOf(raw, declared = raw.size - 1)))
        rejected(srcSection(srcBlobOf(raw, declared = 0)))
    }

    @Test
    fun `풀린 길이가 선언보다 짧으면 거부한다`() {
        val raw = rawOf("main.c" to MAIN_C)
        rejected(srcSection(srcBlobOf(raw, declared = raw.size + 1)))
    }

    @Test
    fun `zlib 이 깨지거나 잘리면 거부한다`() {
        val blob = srcBlobOf(rawOf("main.c" to MAIN_C))
        rejected(srcSection(blob.copyOf(12) + ByteArray(blob.size - 12) { 0x5a }))
        rejected(srcSection(blob.copyOf(blob.size - 6)))
        rejected(srcSection(blob.copyOf(10)))
    }

    @Test
    fun `규칙에 어긋난 이름은 거부한다`() {
        val names = listOf("../x.c", "..\\x.c", "sub/x.c", "/x.c", "a..c", ".x.c", "-x.c", "한글.c", "", "a".repeat(101))
        for (name in names) rejected(srcSection(srcBlobOf(rawOf(name to MAIN_C))))
    }

    @Test
    fun `대소문자만 다른 중복 이름은 거부한다`() {
        rejected(srcSection(srcBlobOf(rawOf("main.c" to MAIN_C, "MAIN.C" to TAG_H))))
    }

    @Test
    fun `파일 크기가 남은 길이를 넘으면 거부한다`() {
        val raw = rawOf("main.c" to MAIN_C)
        rejected(srcSection(srcBlobOf(raw.copyOf(raw.size - 1))))
        rejected(srcSection(srcBlobOf("main.c".toByteArray() + 0 + 1)))
        rejected(srcSection(srcBlobOf("main.c".toByteArray())))
    }

    @Test
    fun `한도를 넘는 선언은 풀기 전에 거부한다`() {
        val raw = rawOf("main.c" to MAIN_C)
        rejected(srcSection(srcBlobOf(raw, declared = 64 * 1024 * 1024 + 1)))
        rejected(coffOf(Section(".srcfiles", srcBlobOf(raw)), sizeWords = 8L * 1024 * 1024 + 1))
    }

    @Test
    fun `섹션 데이터가 파일 밖이면 거부한다`() {
        rejected(coffOf(Section(".srcfiles", srcBlobOf(rawOf("main.c" to MAIN_C))), sizeWords = 100_000))
    }

    @Test
    fun `파일 수 한도는 2000개다`() {
        val many = (0 until 2001).map { "f$it.c" to ByteArray(0) }.toTypedArray()
        rejected(srcSection(srcBlobOf(rawOf(*many))))

        out.writeBytes(srcSection(srcBlobOf(rawOf(*many.copyOf(2000).requireNoNulls()))))
        assertEquals(2000, EmbeddedSource.extract(out, dir).size)
    }
}
