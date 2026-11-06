package kr.agv.cfgmanager.core.pkg

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater

/** 섹션 이름을 머리에 적는 방식. 9자 이상 이름은 문자열 표로 간다. */
internal enum class NameStyle { ZERO_OFFSET, SLASH }

internal class Section(val name: String, val data: ByteArray, val style: NameStyle = NameStyle.ZERO_OFFSET)

private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

/**
 * 최소 TI COFF2 파일: 파일 머리 22 B + 섹션 머리 48 B 씩 + 섹션 데이터 + 빈 심볼표 + 문자열 표.
 * C2000 처럼 섹션 크기는 워드 단위로 적는다. 홀수 길이 데이터는 0 한 바이트를 덧붙인다.
 * [sizeWords] 가 있으면 첫 섹션의 크기 값을 그 값으로 속인다.
 */
internal fun coffOf(vararg sections: Section, sizeWords: Long? = null): ByteArray {
    val strtab = ByteArrayOutputStream().apply { write(ByteArray(4)) }
    val names = sections.map { s ->
        if (s.name.length <= 8) {
            s.name.toByteArray().copyOf(8)
        } else {
            val offset = strtab.size()
            strtab.write(s.name.toByteArray() + 0)
            when (s.style) {
                NameStyle.ZERO_OFFSET -> ByteArray(4) + le32(offset)
                NameStyle.SLASH -> "/$offset".toByteArray().copyOf(8)
            }
        }
    }
    val strBytes = strtab.toByteArray()
    ByteBuffer.wrap(strBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(0, strBytes.size)

    val padded = sections.map { if (it.data.size % 2 == 0) it.data else it.data + 0 }
    var dataPtr = 22 + 48 * sections.size
    val symptr = dataPtr + padded.sumOf { it.size }
    val buf = ByteBuffer.allocate(symptr + strBytes.size).order(ByteOrder.LITTLE_ENDIAN)
    buf.putShort(0xC2.toShort()).putShort(sections.size.toShort()).putInt(0).putInt(symptr).putInt(0)
        .putShort(0).putShort(0).putShort(0x9D)
    sections.forEachIndexed { i, _ ->
        val words = if (i == 0 && sizeWords != null) sizeWords.toInt() else padded[i].size / 2
        buf.put(names[i]).putInt(0).putInt(0).putInt(words).putInt(dataPtr)
            .putInt(0).putInt(0).putInt(0).putInt(0).putInt(0).putShort(0).putShort(0)
        dataPtr += padded[i].size
    }
    padded.forEach { buf.put(it) }
    buf.put(strBytes)
    return buf.array()
}

/** 소스 파일 목록을 원본 형식(이름 + NUL + u32 크기 + 내용)으로 잇는다. */
internal fun rawOf(vararg files: Pair<String, ByteArray>): ByteArray {
    val raw = ByteArrayOutputStream()
    for ((name, data) in files) {
        raw.write(name.toByteArray() + 0)
        raw.write(le32(data.size))
        raw.write(data)
    }
    return raw.toByteArray()
}

internal fun zlib(bytes: ByteArray): ByteArray {
    val deflater = Deflater()
    deflater.setInput(bytes)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    deflater.end()
    return out.toByteArray()
}

/** `.srcfiles` 원시 바이트. [declared] 로 원본 길이 값을 속인다. */
internal fun srcBlobOf(raw: ByteArray, declared: Int = raw.size, magic: String = "AGVSRC1\u0000"): ByteArray =
    magic.toByteArray() + le32(declared) + zlib(raw)

internal fun srcCoffOf(vararg files: Pair<String, ByteArray>): ByteArray =
    coffOf(Section(".text", ByteArray(10) { it.toByte() }), Section(".srcfiles", srcBlobOf(rawOf(*files))))
