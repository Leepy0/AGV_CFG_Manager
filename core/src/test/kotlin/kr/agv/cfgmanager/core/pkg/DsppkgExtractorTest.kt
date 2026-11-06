package kr.agv.cfgmanager.core.pkg

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val VER = "1.2.3"
private const val DSPFW_NAME = "AGV_Main_1.2.3.dspfw"
private const val OUT_NAME = "AGV_Main.out"
private const val MAP_NAME = "AGV_Main.map"

private val DSPFW = ByteArray(300) { (it % 7).toByte() }
private val OUT = ByteArray(5000) { (it % 251).toByte() }
private val MAP = "OUTPUT FILE NAME:   <AGV_Main.out>\n".toByteArray()

private const val CENTRAL_COMPRESSED_SIZE = 20
private const val CENTRAL_SIZE = 24

private fun sha(bytes: ByteArray) =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun fileJson(name: String, bytes: ByteArray) =
    """{"name":"$name","orig_name":"x y.bin","size":${bytes.size},"sha256":"${sha(bytes)}"}"""

private fun manifest(
    dspfw: ByteArray = DSPFW,
    out: ByteArray = OUT,
    map: ByteArray = MAP,
    dspfwName: String = DSPFW_NAME,
    outName: String = OUT_NAME,
    mapName: String = MAP_NAME,
) = """{"format":"agv-dsppkg","format_ver":1,"ver":"$VER","unknown":{"a":[1,null]},""" +
    """"files":{"dspfw":${fileJson(dspfwName, dspfw)},"out":${fileJson(outName, out)},""" +
    """"map":${fileJson(mapName, map)}}}"""

private fun String.swap(from: String, to: String): String {
    assertTrue(from in this, "시험 준비 오류: '$from' 없음")
    return replace(from, to)
}

private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        for ((name, data) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
    }
    return bytes.toByteArray()
}

private fun packageOf(
    manifest: String = manifest(),
    dspfw: ByteArray = DSPFW,
    out: ByteArray = OUT,
    map: ByteArray = MAP,
) = zipOf(
    "manifest.json" to manifest.toByteArray(),
    DSPFW_NAME to dspfw,
    OUT_NAME to out,
    MAP_NAME to map,
)

private fun idOf(manifest: String = manifest()) = sha(manifest.toByteArray()).take(16)

/** ZipOutputStream 이 막는 이름을 만들려고 ZIP 안의 이름 바이트를 같은 길이로 바꾼다. */
private fun ByteArray.renameEntry(from: String, to: String): ByteArray {
    assertEquals(from.length, to.length)
    return String(this, Charsets.ISO_8859_1).swap(from, to).toByteArray(Charsets.ISO_8859_1)
}

/** java.util.zip 이 믿는 중앙 디렉터리의 크기 값을 속인다. [offset] 은 머리 안 위치다. */
private fun ByteArray.lieInCentral(name: String, offset: Int, size: Int): ByteArray {
    val copy = copyOf()
    val buf = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN)
    var pos = 0
    var patched = false
    while (pos + 46 <= copy.size) {
        if (buf.getInt(pos) != 0x02014b50) {
            pos++
            continue
        }
        val nameLength = buf.getShort(pos + 28).toInt()
        if (String(copy, pos + 46, nameLength) == name) {
            buf.putInt(pos + offset, size)
            patched = true
        }
        pos += 46 + nameLength
    }
    assertTrue(patched, "시험 준비 오류: 중앙 디렉터리에 $name 없음")
    return copy
}

class DsppkgExtractorTest {
    private lateinit var root: File
    private lateinit var dir: File
    private lateinit var pkg: File

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("dsppkg").toFile()
        dir = File(root, "out")
        pkg = File(root, "slot.dsppkg")
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun rejected(
        expected: String,
        bytes: ByteArray,
        id: String = idOf(),
        ver: String = VER,
        limits: Map<String, Long> = PKG_LIMITS,
    ) {
        pkg.writeBytes(bytes)
        val e = assertFailsWith<PkgFormatException> { DsppkgExtractor.extract(pkg, id, ver, dir, limits) }
        assertTrue(expected in e.message.orEmpty(), "거부 이유가 다름: ${e.message}")
        assertEquals(emptyList(), dir.list().orEmpty().toList(), "거부 뒤 dir 이 비어 있어야 함")
        assertEquals(setOf("out", "slot.dsppkg"), root.list().orEmpty().toSet() + "out", "dir 밖에 쓰면 안 됨")
    }

    private fun rejectedManifest(expected: String, manifest: String) =
        rejected(expected, packageOf(manifest), idOf(manifest))

    @Test
    fun `규격 패키지는 out 과 map 만 남긴다`() {
        pkg.writeBytes(packageOf())

        val files = DsppkgExtractor.extract(pkg, idOf(), VER, dir)

        assertEquals(PkgFiles(File(dir, OUT_NAME), File(dir, MAP_NAME)), files)
        assertContentEquals(OUT, files.out.readBytes())
        assertContentEquals(MAP, files.map.readBytes())
        assertEquals(setOf(OUT_NAME, MAP_NAME), dir.list().orEmpty().toSet())
    }

    @Test
    fun `내장 소스가 있는 out 이면 src 폴더에 함께 푼다`() {
        val out = srcCoffOf("main.c" to "// 한글\n".toByteArray(), "tag.h" to "#define T\n".toByteArray())
        val manifest = manifest(out = out)
        pkg.writeBytes(packageOf(manifest, out = out))

        val files = DsppkgExtractor.extract(pkg, idOf(manifest), VER, dir)

        assertEquals(listOf(File(dir, "src/main.c"), File(dir, "src/tag.h")), files.sources)
        assertContentEquals("// 한글\n".toByteArray(), files.sources[0].readBytes())
        assertContentEquals(out, files.out.readBytes())
        assertEquals(setOf(OUT_NAME, MAP_NAME, "src"), dir.list().orEmpty().toSet())
    }

    @Test
    fun `내장 소스가 손상되면 패키지를 거부한다`() {
        val out = coffOf(Section(".srcfiles", srcBlobOf(rawOf("../x.c" to ByteArray(3)))))
        val manifest = manifest(out = out)

        rejected("내장 소스 손상", packageOf(manifest, out = out), idOf(manifest))
    }

    @Test
    fun `확장자 대소문자는 무시한다`() {
        val manifest = manifest(dspfwName = "A.DSPFW", outName = "A.Out", mapName = "A.MAP")
        pkg.writeBytes(
            zipOf(
                "manifest.json" to manifest.toByteArray(),
                "A.DSPFW" to DSPFW,
                "A.Out" to OUT,
                "A.MAP" to MAP,
            ),
        )

        val files = DsppkgExtractor.extract(pkg, idOf(manifest), VER, dir)

        assertEquals("A.Out", files.out.name)
        assertEquals("A.MAP", files.map.name)
    }

    @Test
    fun `manifest 의 대문자 sha256 도 받는다`() {
        val manifest = manifest().swap(sha(OUT), sha(OUT).uppercase())
        pkg.writeBytes(packageOf(manifest))

        DsppkgExtractor.extract(pkg, idOf(manifest), VER, dir)
    }

    @Test
    fun `항목이 5개면 거부한다`() {
        rejected(
            "항목 수",
            zipOf(
                "manifest.json" to manifest().toByteArray(),
                DSPFW_NAME to DSPFW,
                OUT_NAME to OUT,
                MAP_NAME to MAP,
                "extra.map" to MAP,
            ),
        )
    }

    @Test
    fun `항목이 빠지면 거부한다`() {
        rejected(
            "항목 수",
            zipOf("manifest.json" to manifest().toByteArray(), DSPFW_NAME to DSPFW, OUT_NAME to OUT),
        )
    }

    @Test
    fun `manifest 가 없으면 거부한다`() {
        rejected(
            "중복",
            zipOf(
                DSPFW_NAME to DSPFW,
                OUT_NAME to OUT,
                MAP_NAME to MAP,
                "second.out" to OUT,
            ),
        )
    }

    @Test
    fun `같은 종류가 둘이면 거부한다`() {
        rejected(
            "중복",
            zipOf(
                "manifest.json" to manifest().toByteArray(),
                DSPFW_NAME to DSPFW,
                OUT_NAME to OUT,
                "OTHER.OUT" to OUT,
            ),
        )
    }

    @Test
    fun `완전히 같은 이름이 둘이면 거부한다`() {
        val twin = "AGV_Mai2.out"
        val bytes = zipOf(
            "manifest.json" to manifest().toByteArray(),
            DSPFW_NAME to DSPFW,
            OUT_NAME to OUT,
            twin to OUT,
        ).renameEntry(twin, OUT_NAME)

        rejected("중복", bytes)
    }

    @Test
    fun `manifest 대소문자가 다르면 허용되지 않는 항목이다`() {
        rejected(
            "허용되지 않는 항목",
            zipOf(
                "MANIFEST.JSON" to manifest().toByteArray(),
                DSPFW_NAME to DSPFW,
                OUT_NAME to OUT,
                MAP_NAME to MAP,
            ),
        )
    }

    @Test
    fun `규칙에 어긋난 이름은 거부한다`() {
        val names = listOf(
            "../x.out",
            "..\\x.out",
            "/x.out",
            "C:\\x.out",
            "sub/x.out",
            "sub\\x.out",
            "a..out",
            ".x.out",
            "-x.out",
            "a b.out",
            "한글.out",
            "x.out\n",
            "a".repeat(57) + ".out",
        )
        for (name in names) {
            rejected(
                "항목 이름",
                zipOf(
                    "manifest.json" to manifest(outName = name.trim()).toByteArray(),
                    DSPFW_NAME to DSPFW,
                    name to OUT,
                    MAP_NAME to MAP,
                ),
            )
        }
    }

    @Test
    fun `디렉터리 항목은 거부한다`() {
        rejected(
            "디렉터리",
            zipOf(
                "manifest.json" to manifest().toByteArray(),
                DSPFW_NAME to DSPFW,
                "sub/" to ByteArray(0),
                MAP_NAME to MAP,
            ),
        )
    }

    @Test
    fun `허용되지 않는 확장자는 거부한다`() {
        rejected(
            "허용되지 않는 항목",
            zipOf(
                "manifest.json" to manifest().toByteArray(),
                DSPFW_NAME to DSPFW,
                "AGV_Main.exe" to OUT,
                MAP_NAME to MAP,
            ),
        )
    }

    @Test
    fun `format 이 다르면 거부한다`() {
        rejectedManifest("format", manifest().swap("agv-dsppkg", "other"))
    }

    @Test
    fun `format_ver 가 다르면 거부한다`() {
        rejectedManifest("format_ver", manifest().swap("\"format_ver\":1", "\"format_ver\":2"))
    }

    @Test
    fun `ver 가 인자와 다르면 거부한다`() {
        rejected("ver", packageOf(), ver = "1.2.4")
    }

    @Test
    fun `패키지 ID 가 다르면 거부한다`() {
        rejected("패키지 ID", packageOf(), id = "0123456789abcdef")
    }

    @Test
    fun `패키지 ID 는 ZIP 안 manifest 바이트의 sha256 앞 16자다`() {
        val text = manifest().replace(",", ",  ")
        pkg.writeBytes(packageOf(text))

        DsppkgExtractor.extract(pkg, idOf(text), VER, dir)

        dir.deleteRecursively()
        rejected("패키지 ID", packageOf(text), id = idOf(manifest()))
    }

    @Test
    fun `파일 sha256 이 다르면 거부한다`() {
        val bad = OUT.copyOf().also { it[0] = (it[0] + 1).toByte() }
        rejected("sha256", packageOf(out = bad))
    }

    @Test
    fun `dspfw 와 map 의 sha256 도 확인한다`() {
        rejected("sha256", packageOf(dspfw = DSPFW.reversedArray()))
        rejected("sha256", packageOf(map = MAP.reversedArray()))
    }

    @Test
    fun `manifest 의 size 가 실제와 다르면 거부한다`() {
        rejectedManifest("size", manifest().swap("\"size\":${OUT.size}", "\"size\":${OUT.size + 1}"))
    }

    @Test
    fun `manifest 의 name 이 항목 이름과 다르면 거부한다`() {
        rejectedManifest("name", manifest(outName = "Other.out"))
    }

    @Test
    fun `필수 키가 없거나 형이 다르면 PkgFormatException 이다`() {
        val base = manifest()
        val sizeKey = "\"size\":${OUT.size}"
        val shaKey = "\"sha256\":\"${sha(OUT)}\""
        val broken = listOf(
            "not json",
            "[1,2]",
            "\"text\"",
            "null",
            "",
            base.swap("\"format\":\"agv-dsppkg\",", ""),
            base.swap("\"format\":\"agv-dsppkg\"", "\"format\":7"),
            base.swap("\"format\":\"agv-dsppkg\"", "\"format\":null"),
            base.swap("\"format_ver\":1,", ""),
            base.swap("\"format_ver\":1", "\"format_ver\":\"1\""),
            base.swap("\"format_ver\":1", "\"format_ver\":1.0"),
            base.swap("\"format_ver\":1", "\"format_ver\":true"),
            base.swap("\"format_ver\":1", "\"format_ver\":{}"),
            base.swap("\"ver\":\"$VER\",", ""),
            base.swap("\"ver\":\"$VER\"", "\"ver\":123"),
            base.swap("\"files\":{", "\"nofiles\":{"),
            base.swap("\"files\":{", "\"files\":[],\"x\":{"),
            base.swap("\"out\":{", "\"noout\":{"),
            base.swap("\"out\":{\"name\":\"$OUT_NAME\"", "\"out\":[\"$OUT_NAME\"],\"x\":{\"name\":\"\""),
            base.swap("\"name\":\"$OUT_NAME\",", ""),
            base.swap("\"name\":\"$OUT_NAME\"", "\"name\":1"),
            base.swap(sizeKey, "\"nosize\":1"),
            base.swap(sizeKey, "\"size\":\"${OUT.size}\""),
            base.swap(sizeKey, "\"size\":99999999999999999999"),
            base.swap(shaKey, "\"nosha\":\"x\""),
            base.swap(shaKey, "\"sha256\":5"),
        )
        for (text in broken) {
            rejectedManifest("manifest", text)
        }
    }

    @Test
    fun `손상된 ZIP 은 PkgFormatException 이다`() {
        val good = packageOf()
        rejected("ZIP", ByteArray(0))
        rejected("ZIP", ByteArray(4096) { 0x5a })
        rejected("ZIP", good.copyOf(good.size / 2))
    }

    @Test
    fun `압축 데이터가 깨지면 거부한다`() {
        val bytes = packageOf()
        val start = String(bytes, Charsets.ISO_8859_1).indexOf(OUT_NAME) + OUT_NAME.length
        for (i in start until start + 200) bytes[i] = 0xff.toByte()

        pkg.writeBytes(bytes)
        assertFailsWith<PkgFormatException> { DsppkgExtractor.extract(pkg, idOf(), VER, dir) }
        assertEquals(emptyList(), dir.list().orEmpty().toList())
    }

    @Test
    fun `압축 데이터가 도중에 끝나면 거부한다`() {
        rejected("ZIP", packageOf().lieInCentral(OUT_NAME, CENTRAL_COMPRESSED_SIZE, 10))
    }

    @Test
    fun `규격 한도 값`() {
        assertEquals(
            mapOf(
                "manifest" to 65_536L,
                "dspfw" to 393_152L,
                "out" to 67_108_864L,
                "map" to 16_777_216L,
            ),
            PKG_LIMITS,
        )
    }

    @Test
    fun `dspfw 는 393152 바이트까지 받는다`() {
        val fits = ByteArray(393_152)
        val manifest = manifest(dspfw = fits)
        pkg.writeBytes(packageOf(manifest, dspfw = fits))
        DsppkgExtractor.extract(pkg, idOf(manifest), VER, dir)

        dir.deleteRecursively()
        val over = ByteArray(393_153)
        val overManifest = manifest(dspfw = over)
        rejected("크기 한도", packageOf(overManifest, dspfw = over), idOf(overManifest))
    }

    @Test
    fun `manifest 는 64 KiB 까지 받는다`() {
        val base = manifest()
        val pad = 65_536 - base.length - ",\"pad\":\"\"".length
        val fits = base.dropLast(1) + ",\"pad\":\"" + "x".repeat(pad) + "\"}"
        assertEquals(65_536, fits.length)
        pkg.writeBytes(packageOf(fits))
        DsppkgExtractor.extract(pkg, idOf(fits), VER, dir)

        dir.deleteRecursively()
        val over = base.dropLast(1) + ",\"pad\":\"" + "x".repeat(pad + 1) + "\"}"
        rejected("크기 한도", packageOf(over), idOf(over))
    }

    @Test
    fun `out 한도는 읽으며 센다`() {
        val limits = PKG_LIMITS + ("out" to OUT.size.toLong())
        pkg.writeBytes(packageOf())
        DsppkgExtractor.extract(pkg, idOf(), VER, dir, limits)

        dir.deleteRecursively()
        rejected("크기 한도", packageOf(), limits = PKG_LIMITS + ("out" to OUT.size - 1L))
    }

    @Test
    fun `map 한도는 읽으며 센다`() {
        rejected("크기 한도", packageOf(), limits = PKG_LIMITS + ("map" to MAP.size - 1L))
    }

    @Test
    fun `선언 크기를 속여도 out 한도를 넘으면 거부한다`() {
        val huge = ByteArray(1 shl 20)
        val manifest = manifest(out = huge)
        val lied = packageOf(manifest, out = huge).lieInCentral(OUT_NAME, CENTRAL_SIZE, 10)

        rejected("크기 한도", lied, idOf(manifest), limits = PKG_LIMITS + ("out" to 1024L))
    }

    @Test
    fun `선언 크기만 속이고 한도 안이면 실제 바이트로 검증한다`() {
        rejected("크기 한도", packageOf().lieInCentral(MAP_NAME, CENTRAL_SIZE, 1), limits = PKG_LIMITS + ("map" to 1L))

        dir.deleteRecursively()
        pkg.writeBytes(packageOf().lieInCentral(OUT_NAME, CENTRAL_SIZE, 1))
        val files = DsppkgExtractor.extract(pkg, idOf(), VER, dir)
        assertContentEquals(OUT, files.out.readBytes())
    }

    @Test
    fun `패키지 파일이 없으면 IOException 이다`() {
        assertFailsWith<IOException> { DsppkgExtractor.extract(pkg, idOf(), VER, dir) }
    }

    @Test
    fun `dir 을 만들 수 없으면 IOException 이다`() {
        pkg.writeBytes(packageOf())
        dir.writeText("파일이 자리를 차지함")

        assertFailsWith<IOException> { DsppkgExtractor.extract(pkg, idOf(), VER, dir) }
    }

    private fun leaveOldFiles() {
        File(dir, "sub").mkdirs()
        File(dir, "sub/Old.map").writeText("old")
        File(dir, "Old.out").writeText("old")
        File(dir, OUT_NAME).writeText("old")
    }

    @Test
    fun `dir 의 이전 파일은 성공 뒤 사라진다`() {
        leaveOldFiles()
        pkg.writeBytes(packageOf())

        val files = DsppkgExtractor.extract(pkg, idOf(), VER, dir)

        assertContentEquals(OUT, files.out.readBytes())
        assertEquals(setOf(OUT_NAME, MAP_NAME), dir.list().orEmpty().toSet())
    }

    @Test
    fun `dir 의 이전 파일은 거부 뒤 사라진다`() {
        leaveOldFiles()

        rejected("패키지 ID", packageOf(), id = "0123456789abcdef")
    }

    @Test
    fun `내용 검증에서 거부되면 먼저 쓴 파일도 지운다`() {
        rejected("sha256", packageOf(map = MAP.reversedArray()))
    }

    // 다른 OS 는 열린 파일도 지울 수 있어 이 방법으로 재현되지 않는다
    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `dir 을 비우지 못하면 IOException 이다`() {
        pkg.writeBytes(packageOf())
        dir.mkdirs()
        File(dir, "Old.out").outputStream().use {
            assertFailsWith<IOException> { DsppkgExtractor.extract(pkg, idOf(), VER, dir) }
        }
    }

    @Test
    fun `pkg 가 dir 안에 있으면 지우지 않고 거부한다`() {
        val inside = File(dir, "sub/slot.dsppkg")
        inside.parentFile.mkdirs()
        inside.writeBytes(packageOf())

        assertFailsWith<IllegalArgumentException> { DsppkgExtractor.extract(inside, idOf(), VER, dir) }

        assertContentEquals(packageOf(), inside.readBytes())
    }

    @Test
    fun `거부 메시지의 항목 이름은 80자로 자른다`() {
        val long = "a".repeat(300)
        for (name in listOf("$long.out", "$long/", "$long/x.out")) {
            pkg.writeBytes(
                zipOf(
                    "manifest.json" to manifest().toByteArray(),
                    DSPFW_NAME to DSPFW,
                    name to OUT,
                    MAP_NAME to MAP,
                ),
            )

            val e = assertFailsWith<PkgFormatException> { DsppkgExtractor.extract(pkg, idOf(), VER, dir) }

            assertTrue("a".repeat(80) in e.message.orEmpty(), "이름이 메시지에 있어야 함: ${e.message}")
            assertFalse("a".repeat(81) in e.message.orEmpty(), "이름이 잘리지 않음: ${e.message}")
        }
    }

    @Test
    fun `프로그램 오류의 IllegalArgumentException 은 ZIP 손상으로 바꾸지 않는다`() {
        val broken = object : Map<String, Long> by PKG_LIMITS {
            override fun get(key: String): Long = throw IllegalArgumentException("프로그램 오류")
        }
        pkg.writeBytes(packageOf())

        val e = assertFailsWith<IllegalArgumentException> { DsppkgExtractor.extract(pkg, idOf(), VER, dir, broken) }

        assertEquals("프로그램 오류", e.message)
        assertEquals(emptyList(), dir.list().orEmpty().toList())
    }
}
