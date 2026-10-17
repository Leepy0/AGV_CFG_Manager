package kr.agv.cfgmanager.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GatewayProbeTest {

    @Test
    fun `HEAD 가 상태 코드를 주면 응답 있음이다`() = runTest {
        val probe = GatewayProbe(HttpHead { url, _ ->
            assertEquals("http://192.168.10.10:8080/", url)
            200
        })
        assertEquals(GatewayResult.Responded(200), probe.check("192.168.10.10"))
    }

    @Test
    fun `404 여도 PC 가 답한 것이므로 응답 있음이다`() = runTest {
        val probe = GatewayProbe(HttpHead { _, _ -> 404 })
        assertEquals(GatewayResult.Responded(404), probe.check("192.168.10.10"))
    }

    @Test
    fun `답이 없으면 응답 없음이다`() = runTest {
        val probe = GatewayProbe(HttpHead { _, _ -> null })
        assertEquals(GatewayResult.NoResponse, probe.check("192.168.10.10"))
    }

    @Test
    fun `연결 실패 예외는 응답 없음이다`() = runTest {
        val probe = GatewayProbe(HttpHead { _, _ -> throw IOException("connection refused") })
        assertEquals(GatewayResult.NoResponse, probe.check("192.168.10.10"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `3초를 넘기면 응답 없음으로 끊는다`() = runTest {
        val probe = GatewayProbe(HttpHead { _, _ ->
            delay(10_000L)
            200
        })
        assertEquals(GatewayResult.NoResponse, probe.check("192.168.10.10"))
        assertEquals(3_000L, testScheduler.currentTime)
    }

    @Test
    fun `서버 주소가 IPv4 가 아니면 요청하지 않는다`() = runTest {
        var called = false
        val probe = GatewayProbe(HttpHead { _, _ ->
            called = true
            200
        })
        assertEquals(GatewayResult.InvalidServer, probe.check("server.local"))
        assertTrue(!called)
    }

    @Test
    fun `게이트웨이 주소는 8080 포트 루트다`() {
        assertEquals("http://192.168.10.10:8080/", GatewayUrl.root("192.168.10.10"))
        assertNull(GatewayUrl.root("999.1.1.1"))
    }
}
