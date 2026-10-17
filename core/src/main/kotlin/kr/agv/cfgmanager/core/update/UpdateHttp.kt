package kr.agv.cfgmanager.core.update

import java.io.Closeable
import java.io.InputStream

/**
 * HTTP 응답 한 건. [body] 와 연결은 [close] 로 닫는다.
 * 2xx 가 아니면 [body] 는 비어 있다. 3xx 를 따라가지 않도록 요청하면 [location] 으로 주소를 얻는다.
 */
class HttpReply(
    val status: Int,
    val location: String?,
    val contentLength: Long,
    val body: InputStream,
    private val onClose: () -> Unit = {},
) : Closeable {
    override fun close() {
        try {
            body.close()
        } finally {
            onClose()
        }
    }
}

/**
 * 업데이트 전용 HTTP 계층 (인터페이스). 앱은 HttpURLConnection 으로 구현하고, 테스트는 가짜 응답을 넣는다.
 * 호출은 블로킹이므로 호출 측이 IO 디스패처에서 부른다.
 */
fun interface UpdateHttp {
    /** [url] 을 GET 한다. [followRedirects] 가 false 면 3xx 를 그대로 돌려준다. 실패는 IOException. */
    fun get(url: String, followRedirects: Boolean, timeoutMs: Int): HttpReply
}
