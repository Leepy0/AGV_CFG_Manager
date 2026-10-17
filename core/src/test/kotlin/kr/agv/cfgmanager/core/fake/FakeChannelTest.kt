package kr.agv.cfgmanager.core.fake

import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.ChannelState
import kr.agv.cfgmanager.core.bridge.ResponseParsers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FakeChannelTest {

    @Test
    fun `연결하면 환영 문구를 받고 로그인해 Connected 가 된다`() = runTest {
        val channel = FakeBridgeChannel(FakeDevice())
        assertTrue(channel.waitReady(10.seconds))
        assertIs<ChannelState.Connected>(channel.state.value)
    }

    @Test
    fun `잘못된 비밀번호로는 준비되지 않는다`() = runTest {
        val channel = FakeBridgeChannel(FakeDevice(), loginPassword = "wrong-password")
        assertEquals(false, channel.waitReady(10.seconds))
        assertIs<ChannelState.Disconnected>(channel.state.value)
    }

    @Test
    fun `응답을 청크로 잘라 보내도 원문과 파싱 결과가 같다`() = runTest {
        val channel = FakeBridgeChannel(FakeDevice(), chunkSize = 5)
        channel.waitReady(10.seconds)
        val cfg = channel.exec("cfg")
        assertTrue(ResponseParsers.parseCfg(cfg) != null)
        assertTrue(cfg.contains("\r\n"), "BLE 응답은 CRLF")
    }

    @Test
    fun `명령을 받기 전에 끊으면 Disconnected 이고 다시 붙을 수 있다`() = runTest {
        val channel = FakeBridgeChannel(FakeDevice())
        channel.waitReady(10.seconds)
        channel.dropBefore = { it == "cfg" }
        assertFailsWith<BridgeChannelException.Disconnected> { channel.exec("cfg") }
        assertIs<ChannelState.Disconnected>(channel.state.value)
        channel.dropBefore = null
        assertTrue(channel.waitReady(10.seconds))
    }

    @Test
    fun `응답하지 않으면 시간 초과`() = runTest {
        val channel = FakeBridgeChannel(FakeDevice())
        channel.waitReady(10.seconds)
        channel.silent = { true }
        assertFailsWith<BridgeChannelException.Timeout> { channel.exec("stat") }
    }

    @Test
    fun `reboot 는 Rebooting 을 거쳐 다시 준비되고 저장 설정이 올라온다`() = runTest {
        val device = FakeDevice()
        val channel = FakeBridgeChannel(device)
        channel.waitReady(10.seconds)
        channel.exec("set agv 7")
        channel.exec("save")
        val reply = channel.execThenDisconnect("reboot")
        assertEquals("rebooting\r\n", reply)
        assertIs<ChannelState.Rebooting>(channel.state.value)
        assertTrue(channel.waitReady(30.seconds))
        assertEquals(7, device.running.agv)
    }

    @Test
    fun `알 수 없는 명령의 응답은 CRLF 로 온다`() = runTest {
        val channel = FakeBridgeChannel(FakeDevice())
        channel.waitReady(10.seconds)
        assertEquals("unknown command 'x' - type help\r\n", channel.exec("x"))
    }
}
