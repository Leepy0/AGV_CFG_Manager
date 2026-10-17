package kr.agv.cfgmanager.core.util

/**
 * 청크 경계에서 잘린 UTF-8 문자를 기다렸다가 디코드한다. BLE 알림 한 조각이 한글 중간에서 끊길 수 있다.
 */
class Utf8Stream {
    private var pending = ByteArray(0)

    /** 새 바이트를 넣고, 완성된 문자까지의 텍스트를 돌려준다. 미완성 꼬리는 다음 호출로 넘어간다. */
    fun push(chunk: ByteArray): String {
        val all = if (pending.isEmpty()) chunk else pending + chunk
        val cut = completeLength(all)
        pending = all.copyOfRange(cut, all.size)
        return String(all, 0, cut, Charsets.UTF_8)
    }

    /** 스트림이 끝났을 때 남은 바이트를 (깨진 문자 포함) 그대로 디코드한다. */
    fun flush(): String {
        val rest = pending
        pending = ByteArray(0)
        return String(rest, Charsets.UTF_8)
    }

    private fun completeLength(bytes: ByteArray): Int {
        val n = bytes.size
        var back = 1
        while (back <= minOf(3, n)) {
            val b = bytes[n - back].toInt() and 0xFF
            if ((b and 0xC0) == 0x80) {       // 연속 바이트: 시작 바이트를 더 거슬러 올라간다
                back++
                continue
            }
            val need = when {
                (b and 0x80) == 0 -> 1
                (b and 0xE0) == 0xC0 -> 2
                (b and 0xF0) == 0xE0 -> 3
                (b and 0xF8) == 0xF0 -> 4
                else -> 1
            }
            return if (need > back) n - back else n
        }
        return n
    }
}

/** 문자열의 UTF-8 바이트 길이. 브리지 버퍼 한도는 바이트 기준이다. */
fun String.utf8Length(): Int = toByteArray(Charsets.UTF_8).size
