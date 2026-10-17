package kr.agv.cfgmanager.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kr.agv.cfgmanager.core.util.Ipv4
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** HTTP HEAD 한 번. 응답 상태 코드를 주고, 응답이 없으면 null 을 준다. */
fun interface HttpHead {
    suspend fun head(url: String, timeoutMs: Int): Int?
}

/** 게이트웨이 도달 판정 결과 (docs/DEVELOPMENT_PLAN.md F-D4). */
sealed interface GatewayResult {
    /** 게이트웨이 PC 가 HTTP 로 답했다. 상태 코드가 어떻든 PC 는 살아 있다. */
    data class Responded(val status: Int) : GatewayResult

    /** 제한 시간 안에 답이 없다. 휴대폰이 같은 Wi-Fi 에 있는지, PC 가 켜졌는지 확인한다. */
    data object NoResponse : GatewayResult

    /** 브리지 `cfg` 의 server 값이 IPv4 가 아니다. */
    data object InvalidServer : GatewayResult
}

object GatewayUrl {
    /** 게이트웨이 웹 화면 포트 (게이트웨이 0.3.0). */
    const val PORT = 8080

    /** `http://<server>:8080/`. server 가 IPv4 가 아니면 null. */
    fun root(server: String): String? = Ipv4.canonical(server)?.let { "http://$it:$PORT/" }
}

/** 장치의 server 주소로 게이트웨이 화면에 HEAD 를 보내 응답 여부를 본다. 3초 안에 답하지 않으면 없음이다. */
class GatewayProbe(
    private val http: HttpHead = HttpUrlHead,
    private val timeoutMs: Int = TIMEOUT_MS,
) {
    suspend fun check(server: String): GatewayResult {
        val url = GatewayUrl.root(server) ?: return GatewayResult.InvalidServer
        val status = withTimeoutOrNull(timeoutMs.toLong()) {
            try {
                http.head(url, timeoutMs)
            } catch (failure: IOException) {
                null
            }
        }
        return status?.let { GatewayResult.Responded(it) } ?: GatewayResult.NoResponse
    }

    companion object {
        const val TIMEOUT_MS = 3_000
    }
}

/** 실제 HTTP 구현. 휴대폰 네트워크로 HEAD 를 보내고 리다이렉트는 따라가지 않는다. */
object HttpUrlHead : HttpHead {
    override suspend fun head(url: String, timeoutMs: Int): Int? = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.responseCode
        } catch (failure: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
