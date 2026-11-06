package kr.agv.cfgmanager.core.pkg

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * TI COFF `.out` 의 `.srcfiles` 섹션(PC 도구 embed_src 가 넣음)에서 소스 파일을 꺼낸다.
 * 섹션 원시 바이트: `AGVSRC1\0` + u32 LE 원본 길이 + zlib. 원본은 파일마다 (이름 + NUL + u32 LE 크기 + 내용).
 */
object EmbeddedSource {
    // 섹션은 패키지(보관 한도 6 MB) 안에 든다. 원본은 메모리에 올리지 않고 풀면서 바로 쓰므로 디스크만 쓴다
    private const val SECTION_MAX = 6L * 1024 * 1024
    private const val RAW_MAX = 128L * 1024 * 1024
    private const val FILE_MAX = 32L * 1024 * 1024
    private const val COUNT_MAX = 2000
    private const val NAME_MAX = 100
    private const val FILE_HEADER = 22
    private const val SYMBOL_SIZE = 18
    private const val BUFFER_SIZE = 8192
    private val SECTION_NAME = ".srcfiles\u0000".toByteArray()
    private val MAGIC = "AGVSRC1\u0000".toByteArray()
    private val NAME_RE = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")

    /**
     * [out] 에 내장된 소스를 [dir] 에 풀고 쓴 파일을 돌려준다. 섹션이 없으면 빈 목록이고 [dir] 을 만들지 않는다.
     * 실패하면 [dir] 은 남지 않는다. 블로킹 I/O 다.
     *
     * dir 은 이 함수가 소유한다. 시작할 때 통째로 지운다.
     *
     * @throws PkgFormatException 섹션은 있는데 형식이 깨졌다.
     * @throws IOException [out] 을 읽거나 [dir] 을 비우거나 쓰지 못했다.
     */
    fun extract(out: File, dir: File): List<File> {
        if (!dir.deleteRecursively()) throw IOException("폴더를 비우지 못함: $dir")
        val blob = RandomAccessFile(out, "r").use { findSection(it) } ?: return emptyList()
        if (blob.size < MAGIC.size + 4 || !blob.copyOf(MAGIC.size).contentEquals(MAGIC)) corrupt("형식 표시 다름")
        val declared = ByteBuffer.wrap(blob, MAGIC.size, 4).order(ByteOrder.LITTLE_ENDIAN).getInt().u32()
        if (declared > RAW_MAX) corrupt("원본 길이 $declared B")

        var done = false
        try {
            val files = RawReader(blob, MAGIC.size + 4, declared).use { unpack(it, dir) }
            done = true
            return files
        } finally {
            if (!done) dir.deleteRecursively()
        }
    }

    private fun corrupt(why: String): Nothing = throw PkgFormatException("내장 소스 손상: $why")

    /** 범위를 벗어나면 null. COFF 머리가 깨진 파일은 섹션이 없는 것으로 본다. */
    private fun RandomAccessFile.readAt(pos: Long, len: Int): ByteBuffer? {
        if (pos < 0 || pos + len > length()) return null
        val bytes = ByteArray(len)
        seek(pos)
        readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun Int.u32() = toLong() and 0xffffffffL
    private fun Short.u16() = toInt() and 0xffff

    private fun findSection(file: RandomAccessFile): ByteArray? {
        val head = file.readAt(0, FILE_HEADER) ?: return null
        val vid = head.getShort(0).u16()
        if (vid != 0xC1 && vid != 0xC2) return null
        val count = head.getShort(2).u16()
        val strtab = head.getInt(8).u32() + head.getInt(12).u32() * SYMBOL_SIZE
        val headerSize = if (vid == 0xC2) 48 else 40
        var pos = FILE_HEADER.toLong() + head.getShort(16).u16()
        repeat(count) {
            val sec = file.readAt(pos, 24) ?: return null
            pos += headerSize
            val name = ByteArray(8).also { sec.get(0, it) }
            val nameOffset = when {
                name[0] == '/'.code.toByte() -> String(name, 1, 7).substringBefore('\u0000').toLongOrNull()
                sec.getInt(0) == 0 -> sec.getInt(4).u32()
                else -> null
            }
            val isTarget = nameOffset != null &&
                file.readAt(strtab + nameOffset, SECTION_NAME.size)?.array()?.contentEquals(SECTION_NAME) == true
            val scnptr = sec.getInt(20).u32()
            if (isTarget && scnptr != 0L) {
                // C2000 은 크기가 워드 단위다
                val size = sec.getInt(16).u32() * 2
                if (size > SECTION_MAX) corrupt("섹션 크기 $size B")
                return file.readAt(scnptr, size.toInt())?.array() ?: corrupt("섹션이 파일 밖")
            }
        }
        return null
    }

    /** 선언 길이까지만 푼다. zlib 스트림 뒤 여분(홀수 패딩)은 무시한다. */
    private class RawReader(blob: ByteArray, start: Int, declared: Long) : AutoCloseable {
        private val inflater = Inflater().apply { setInput(blob, start, blob.size - start) }
        private val buffer = ByteArray(BUFFER_SIZE)
        private var pos = 0
        private var limit = 0

        /** 아직 읽지 않은 원본 바이트 수. */
        var remaining = declared
            private set

        private fun fill() {
            val want = minOf(remaining, BUFFER_SIZE.toLong()).toInt()
            val n = try {
                inflater.inflate(buffer, 0, want)
            } catch (e: DataFormatException) {
                corrupt("zlib ${e.message}")
            }
            if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) {
                corrupt("원본 길이 부족")
            }
            pos = 0
            limit = n
        }

        /** [remaining] 이 0 보다 클 때만 부른다. */
        fun readByte(): Byte {
            while (pos == limit) fill()
            remaining--
            return buffer[pos++]
        }

        fun copyTo(target: OutputStream, size: Long) {
            var left = size
            while (left > 0) {
                while (pos == limit) fill()
                val n = minOf(left, (limit - pos).toLong()).toInt()
                target.write(buffer, pos, n)
                pos += n
                left -= n
                remaining -= n
            }
        }

        /** 선언 길이를 다 읽은 뒤 스트림도 끝나야 한다. */
        fun finish() {
            try {
                if (!inflater.finished() && (inflater.inflate(ByteArray(1)) > 0 || !inflater.finished())) {
                    corrupt("원본 길이 초과 또는 스트림 끝 없음")
                }
            } catch (e: DataFormatException) {
                corrupt("zlib ${e.message}")
            }
        }

        override fun close() = inflater.end()
    }

    private fun unpack(reader: RawReader, dir: File): List<File> {
        val files = ArrayList<File>()
        val seen = HashSet<String>()
        while (reader.remaining > 0) {
            if (files.size == COUNT_MAX) corrupt("파일 수 $COUNT_MAX 개 초과")
            val name = readName(reader)
            if (!NAME_RE.matches(name) || ".." in name) corrupt("이름 규칙 위반: ${name.take(80)}")
            if (!seen.add(name.lowercase())) corrupt("이름 중복: $name")
            if (reader.remaining < 4) corrupt("크기 없음: $name")
            val size = LongArray(4) { (reader.readByte().toLong() and 0xff) shl (8 * it) }.sum()
            if (size > FILE_MAX || size > reader.remaining) corrupt("파일 크기 $size B: $name")
            Files.createDirectories(dir.toPath())
            val file = File(dir, name)
            file.outputStream().use { reader.copyTo(it, size) }
            files += file
        }
        reader.finish()
        return files
    }

    /** 이름은 100바이트까지라 NUL 을 그 안에서만 찾는다. */
    private fun readName(reader: RawReader): String {
        val name = ByteArrayOutputStream(NAME_MAX)
        while (reader.remaining > 0) {
            val b = reader.readByte()
            // 규칙 밖 바이트는 ISO-8859-1 로 읽어도 정규식에서 걸린다
            if (b == 0.toByte()) return name.toString(Charsets.ISO_8859_1.name())
            if (name.size() == NAME_MAX) break
            name.write(b.toInt())
        }
        corrupt("이름 끝 없음")
    }
}
