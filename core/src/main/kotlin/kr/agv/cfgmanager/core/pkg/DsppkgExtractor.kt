package kr.agv.cfgmanager.core.pkg

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** 패키지가 규격에 맞지 않거나 ZIP 이 깨졌다. */
class PkgFormatException(message: String) : Exception(message)

data class PkgFiles(val out: File, val map: File)

// 압축 해제 기준 한도 (DSP_OTA_DESIGN.md 3-1절). ZIP 선언값이 아니라 읽으며 센다.
internal val PKG_LIMITS = mapOf(
    "manifest" to 64L * 1024,
    "dspfw" to 393_152L,
    "out" to 64L * 1024 * 1024,
    "map" to 16L * 1024 * 1024,
)

/** `.dsppkg` 를 규격대로 검증해 푼다. 규격: DSP_OTA_DESIGN.md 3-1절. 링크 시각은 다시 확인하지 않는다. */
object DsppkgExtractor {
    private const val MANIFEST_NAME = "manifest.json"
    private const val FORMAT = "agv-dsppkg"
    private const val FORMAT_VER = 1L
    private const val ENTRY_COUNT = 4

    // 항목 이름은 영숫자로 시작하므로 이 폴더와 겹치지 않는다
    private const val TEMP_DIR = ".extracting"
    private val NAME_RE = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,59}")
    private val FILE_KINDS = listOf("dspfw", "out", "map")

    /**
     * [pkg] 를 검증하고 `.out` · `.map` 을 [dir] 에 항목 이름 그대로 둔다. 실패하면 [dir] 에 새 파일을 남기지 않는다.
     * 블로킹 I/O 다.
     *
     * @throws PkgFormatException 패키지가 규격에 맞지 않거나 ZIP 이 깨졌다.
     * @throws IOException 패키지를 읽지 못했거나 [dir] 에 쓰지 못했다. 패키지 잘못이 아니다.
     */
    fun extract(pkg: File, id: String, ver: String, dir: File): PkgFiles = extract(pkg, id, ver, dir, PKG_LIMITS)

    internal fun extract(pkg: File, id: String, ver: String, dir: File, limits: Map<String, Long>): PkgFiles {
        val temp = File(dir, TEMP_DIR)
        temp.deleteRecursively()
        Files.createDirectories(temp.toPath())
        try {
            return ZipFile(pkg).use { zip ->
                val entries = classify(zip)
                verifyInto(temp, zip, entries, id, ver, limits)
                promote(temp, dir, entries.getValue("out").name, entries.getValue("map").name)
            }
        } catch (e: ZipException) {
            throw PkgFormatException("ZIP 손상: ${e.message}")
        } catch (e: EOFException) {
            throw PkgFormatException("ZIP 손상: ${e.message}")
        } catch (e: IllegalArgumentException) {
            // 항목 이름 인코딩이 깨지면 런타임에 따라 ZipException 이 아니라 이 예외가 난다
            throw PkgFormatException("ZIP 손상: ${e.message}")
        } finally {
            temp.deleteRecursively()
        }
    }

    /** 내용을 읽기 전에 이름만으로 판단한다. 항목 4개가 서로 다른 종류면 네 종류가 다 있다. */
    private fun classify(zip: ZipFile): Map<String, ZipEntry> {
        val all = zip.entries().toList()
        if (all.size != ENTRY_COUNT) throw PkgFormatException("항목 수 ${all.size}개")
        val entries = HashMap<String, ZipEntry>()
        for (entry in all) {
            if (entries.put(kindOf(entry), entry) != null) throw PkgFormatException("항목 중복: ${entry.name}")
        }
        return entries
    }

    private fun kindOf(entry: ZipEntry): String {
        val name = entry.name
        if (entry.isDirectory) throw PkgFormatException("디렉터리 항목: $name")
        if (!NAME_RE.matches(name) || ".." in name) throw PkgFormatException("항목 이름 규칙 위반: $name")
        if (name == MANIFEST_NAME) return "manifest"
        return FILE_KINDS.firstOrNull { name.endsWith(".$it", ignoreCase = true) }
            ?: throw PkgFormatException("허용되지 않는 항목: $name")
    }

    private fun verifyInto(
        temp: File,
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        id: String,
        ver: String,
        limits: Map<String, Long>,
    ) {
        val manifest = ByteArrayOutputStream()
        val digest = zip.copyLimited(entries.getValue("manifest"), limits.getValue("manifest"), manifest).sha256
        if (digest.take(16) != id) throw PkgFormatException("패키지 ID 다름: ${digest.take(16)}")
        val files = parseManifest(manifest.toByteArray().decodeToString(), ver)

        for (kind in FILE_KINDS) {
            val entry = entries.getValue(kind)
            val spec = files.obj(kind)
            if (spec.str("name") != entry.name) throw PkgFormatException("manifest name 다름: ${entry.name}")
            val size = spec.long("size")
            val sha256 = spec.str("sha256")
            val copied = File(temp, entry.name).outputStream().use { zip.copyLimited(entry, limits.getValue(kind), it) }
            if (copied.size != size) throw PkgFormatException("manifest size 다름: ${entry.name}")
            if (copied.sha256 != sha256.lowercase()) throw PkgFormatException("sha256 다름: ${entry.name}")
        }
    }

    /** 공통 키를 확인하고 `files` 를 돌려준다. */
    private fun parseManifest(text: String, ver: String): JsonObject {
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: throw PkgFormatException("manifest 가 JSON 객체가 아님")
        if (root.str("format") != FORMAT) throw PkgFormatException("manifest format 다름")
        if (root.long("format_ver") != FORMAT_VER) throw PkgFormatException("manifest format_ver 다름")
        if (root.str("ver") != ver) throw PkgFormatException("manifest ver 다름")
        return root.obj("files")
    }

    private fun JsonObject.obj(key: String): JsonObject =
        this[key] as? JsonObject ?: throw PkgFormatException("manifest $key 없음 또는 형식 오류")

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw PkgFormatException("manifest $key 없음 또는 형식 오류")

    private fun JsonObject.long(key: String): Long =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toLongOrNull()
            ?: throw PkgFormatException("manifest $key 없음 또는 형식 오류")

    private class Copied(val size: Long, val sha256: String)

    /** ZIP 이 선언한 크기는 쓰지 않는다. 읽으며 세다가 [limit] 를 넘는 순간 멈춘다. */
    private fun ZipFile.copyLimited(entry: ZipEntry, limit: Long, target: OutputStream): Copied {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var size = 0L
        getInputStream(entry).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                size += read
                if (size > limit) throw PkgFormatException("크기 한도 $limit B 초과: ${entry.name}")
                digest.update(buffer, 0, read)
                target.write(buffer, 0, read)
            }
        }
        return Copied(size, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun promote(temp: File, dir: File, outName: String, mapName: String): PkgFiles {
        val out = File(dir, outName)
        val map = File(dir, mapName)
        move(File(temp, outName), out)
        try {
            move(File(temp, mapName), map)
        } catch (e: IOException) {
            out.delete()
            throw e
        }
        return PkgFiles(out, map)
    }

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
