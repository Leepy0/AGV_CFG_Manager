package kr.agv.cfgmanager.core.update

import java.io.ByteArrayInputStream
import java.io.IOException

/** URL 별 응답을 미리 넣는 가짜 HTTP. 요청 기록으로 어떤 주소를 불렀는지 검사한다. */
internal class ScriptedHttp(private val routes: Map<String, (followRedirects: Boolean) -> HttpReply>) : UpdateHttp {
    val calls = mutableListOf<Pair<String, Boolean>>()

    override fun get(url: String, followRedirects: Boolean, timeoutMs: Int): HttpReply {
        calls += url to followRedirects
        val route = routes[url] ?: throw IOException("no route: $url")
        return route(followRedirects)
    }
}

internal fun okReply(body: ByteArray, contentLength: Long = body.size.toLong()): HttpReply =
    HttpReply(200, null, contentLength, ByteArrayInputStream(body))

internal fun statusReply(code: Int, location: String? = null): HttpReply =
    HttpReply(code, location, 0L, ByteArrayInputStream(ByteArray(0)))

internal fun textReply(text: String): HttpReply = okReply(text.toByteArray(Charsets.UTF_8))

internal fun sha256Hex(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal const val SAMPLE_SHA = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
