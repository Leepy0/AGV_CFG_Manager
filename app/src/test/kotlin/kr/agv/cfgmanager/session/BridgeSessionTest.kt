package kr.agv.cfgmanager.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.fake.FakeDevice
import kr.agv.cfgmanager.core.fake.FakeDeviceOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 세션을 가짜 장치로 돌려 연결 · 로그인 · 읽기 · 콘솔 마스킹 · 재부팅을 검증한다 (하드웨어 없음). */
@OptIn(ExperimentalCoroutinesApi::class)
class BridgeSessionTest {

    private val address = "00:AB:CD:00:00:01"

    private fun boards(device: FakeDevice = FakeDevice(FakeDeviceOptions())): FakeBoardSource =
        FakeBoardSource(listOf(FakeBoardEntry(address, device, rssi = -50)), clock = { 0L })

    private fun TestScope.session(device: FakeDevice = FakeDevice(FakeDeviceOptions())): BridgeSession =
        BridgeSession(boards(device), backgroundScope, clock = { 0L })

    @Test
    fun `올바른 비밀번호로 연결하면 장치를 읽고 연결됨이 된다`() = runTest {
        val session = session()
        val outcome = session.connect(address, null, "agvsetup")
        assertIs<ConnectOutcome.Connected>(outcome)
        assertEquals(CfgState.DEFAULT, outcome.snapshot.cfgState)
        assertEquals(LinkStatus.Connected, session.state.value.link)
        assertEquals("0.5.5", session.state.value.fw)
    }

    @Test
    fun `콘솔에는 로그인 비밀번호가 가려져서 남는다`() = runTest {
        val session = session()
        session.connect(address, null, "agvsetup")
        val texts = session.console.value.map { it.text }
        assertTrue(texts.contains("> login ***"), "콘솔: $texts")
        assertTrue(texts.none { it.contains("agvsetup") })
    }

    @Test
    fun `틀린 비밀번호는 남은 횟수를 알려 주고 다섯 번째는 연결 끊김으로 본다`() = runTest {
        val session = session()
        val first = session.connect(address, null, "wrong-pass") as ConnectOutcome.Failed
        assertEquals(ConnectFailure.WrongPassword(4), first.failure)
        repeat(3) { session.connect(address, null, "wrong-pass") }
        val fifth = session.connect(address, null, "wrong-pass") as ConnectOutcome.Failed
        assertEquals(ConnectFailure.TooManyFailures, fifth.failure)
        assertEquals(LinkStatus.Disconnected, session.state.value.link)
    }

    @Test
    fun `성공하면 비밀번호 실패 횟수가 초기화된다`() = runTest {
        val session = session()
        session.connect(address, null, "wrong-pass")
        assertIs<ConnectOutcome.Connected>(session.connect(address, null, "agvsetup"))
        val again = session.connect(address, null, "wrong-pass") as ConnectOutcome.Failed
        assertEquals(ConnectFailure.WrongPassword(4), again.failure)
    }

    @Test
    fun `콘솔 명령은 응답을 돌려주고 너무 긴 줄은 보내지 않는다`() = runTest {
        val session = session()
        session.connect(address, null, "agvsetup")
        val stat = session.sendConsole("stat")
        assertTrue(stat.isSuccess)
        assertTrue(stat.getOrThrow().contains("fw=0.5.5"))
        val tooLong = session.sendConsole("set ssid " + "가".repeat(80))
        assertTrue(tooLong.isFailure)
    }

    @Test
    fun `사용자가 끊으면 연결 상태가 끊김이 되고 다시 붙지 않는다`() = runTest {
        val session = session()
        session.connect(address, null, "agvsetup")
        session.disconnect()
        assertEquals(LinkStatus.Disconnected, session.state.value.link)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(LinkStatus.Disconnected, session.state.value.link)
    }

    @Test
    fun `재부팅하면 재부팅 대기를 거쳐 같은 주소로 다시 연결된다`() = runTest {
        val session = session()
        session.connect(address, null, "agvsetup")
        val reply = session.sendConsole("reboot")
        assertTrue(reply.getOrThrow().startsWith("rebooting"))
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(LinkStatus.Connected, session.state.value.link)
    }

    @Test
    fun `설정 모드를 닫으면 자동 재연결 없이 끊김으로 둔다`() = runTest {
        val session = session()
        session.connect(address, null, "agvsetup")
        session.sendConsole("setup off")
        assertEquals(LinkStatus.Disconnected, session.state.value.link)
        assertFalse(session.state.value.notice.isNullOrBlank())
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(LinkStatus.Disconnected, session.state.value.link)
    }
}
