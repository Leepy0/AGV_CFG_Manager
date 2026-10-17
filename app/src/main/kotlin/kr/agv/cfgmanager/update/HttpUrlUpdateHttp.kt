package kr.agv.cfgmanager.update

import kr.agv.cfgmanager.core.update.HttpReply
import kr.agv.cfgmanager.core.update.UpdateHttp
import kr.agv.cfgmanager.core.update.UpdateSpec
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 업데이트용 HTTP (HttpURLConnection). github.com 주소만 허용한다. 리다이렉트는 요청마다 정한다.
 * 블로킹 호출이므로 호출 측이 IO 디스패처에서 부른다 (`UpdateChecker` · `UpdateDownloader`).
 */
object HttpUrlUpdateHttp : UpdateHttp {

    override fun get(url: String, followRedirects: Boolean, timeoutMs: Int): HttpReply {
        if (!url.startsWith(UpdateSpec.REPO_URL)) throw IOException("허용되지 않은 업데이트 주소")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = followRedirects
            connection.useCaches = false
            val status = connection.responseCode
            val body: InputStream = if (status in 200..299) {
                connection.inputStream
            } else {
                ByteArrayInputStream(ByteArray(0))
            }
            return HttpReply(
                status = status,
                location = connection.getHeaderField("Location"),
                contentLength = connection.contentLengthLong,
                body = body,
                onClose = connection::disconnect,
            )
        } catch (failure: IOException) {
            connection.disconnect()
            throw failure
        }
    }
}
