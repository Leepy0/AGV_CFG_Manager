package kr.agv.cfgmanager.batch

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kr.agv.cfgmanager.apply.ApplyMeta
import kr.agv.cfgmanager.apply.ApplyRunner
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.fake.FakeDevice
import kr.agv.cfgmanager.core.fake.FakeDeviceOptions
import kr.agv.cfgmanager.core.workflows.ApplyOutcome
import kr.agv.cfgmanager.core.workflows.BatchState
import kr.agv.cfgmanager.core.workflows.ReadyAdvice
import kr.agv.cfgmanager.data.HistoryStore
import kr.agv.cfgmanager.session.BridgeSession
import kr.agv.cfgmanager.session.ConnectOutcome
import kr.agv.cfgmanager.session.FakeBoardEntry
import kr.agv.cfgmanager.session.FakeBoardSource
import kr.agv.cfgmanager.ui.config.ConfigForm
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 세션 · 가짜 장치 두 대 · 적용 실행기를 묶어 연속 세팅 전체 흐름을 돌린다 (하드웨어 없음). */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchSessionTest {

    private val first = "00:AB:CD:00:00:01"
    private val second = "00:AB:CD:00:00:02"

    private suspend fun TestScope.repeatYield() = repeat(3) { yield() }

    @Test
    fun `두 대를 연속으로 설정하면 호기가 8 로 넘어가고 이력에 프로필이 남는다`() = runTest {
        val devices = listOf(
            FakeDevice(FakeDeviceOptions(mac = "a1:b2:c3:d4:a1:01")),
            FakeDevice(FakeDeviceOptions(mac = "a1:b2:c3:d4:a1:02")),
        )
        val boards = FakeBoardSource(
            listOf(FakeBoardEntry(first, devices[0], -50), FakeBoardEntry(second, devices[1], -60)),
            clock = { 0L },
        )
        val session = BridgeSession(boards, backgroundScope, clock = { 0L })
        val dir = Files.createTempDirectory("batch-session").toFile()
        try {
            val history = HistoryStore(File(dir, "history.jsonl"))
            val runner = ApplyRunner(session, history, this, clock = { 1_700_000_000_000L }, isFake = { true })
            val batch = BatchController(runner.state, backgroundScope)
            yield()

            batch.start(firstAgv = 7, profileName = "본사 1공장")
            val addresses = listOf(first to 7, second to 8)
            for ((address, agv) in addresses) {
                batch.pick(address)
                val outcome = session.connect(address, null, "agvsetup")
                val connected = assertIs<ConnectOutcome.Connected>(outcome)
                batch.readFinished(connected.snapshot.cfgState!!)
                val ready = assertIs<BatchState.Ready>(batch.ui.value.state)
                assertIs<ReadyAdvice.FillNextAgv>(ready.advice)
                assertEquals(agv, ready.nextAgv)

                val draft = ConfigForm.fromDevice(connected.snapshot.config!!).with(ConfigKey.AGV, agv.toString())
                val target = ConfigForm.check(draft).config!!
                runner.start(target, saveOnly = false, meta = ApplyMeta("hq-default", "본사 1공장"))
                val done = runner.state.first { it is ApplyUi.Done } as ApplyUi.Done
                repeatYield()
                assertEquals(ApplyOutcome.Applied, done.result.outcome)
                runner.dismiss()
                repeatYield()
            }

            val armed = assertIs<BatchState.Armed>(batch.ui.value.state)
            assertEquals(9, armed.nextAgv)
            assertEquals(listOf(first, second), armed.done.map { it.bleAddress })

            val entries = history.readAll()
            assertEquals(listOf(7, 8), entries.map { it.agv })
            assertTrue(entries.all { it.profileName == "본사 1공장" && it.profileId == "hq-default" })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `설정된 보드를 고르면 자동 채움 없이 경고 상태로 머문다`() = runTest {
        val device = FakeDevice(FakeDeviceOptions(mac = "a1:b2:c3:d4:a1:09"))
        device.handle("login agvsetup")
        device.handle("set agv 5")
        device.handle("save")
        device.reboot()
        device.onDisconnected()
        val boards = FakeBoardSource(listOf(FakeBoardEntry(first, device, -50)), clock = { 0L })
        val session = BridgeSession(boards, backgroundScope, clock = { 0L })
        val runner = ApplyRunner(session, HistoryStore(File(Files.createTempDirectory("b").toFile(), "h.jsonl")), this, isFake = { true })
        val batch = BatchController(runner.state, backgroundScope)
        yield()

        batch.start(firstAgv = 7, profileName = null)
        batch.pick(first)
        val outcome = assertIs<ConnectOutcome.Connected>(session.connect(first, null, "agvsetup"))
        batch.readFinished(outcome.snapshot.cfgState!!)
        val ready = assertIs<BatchState.Ready>(batch.ui.value.state)
        assertEquals(CfgState.SAVED, outcome.snapshot.cfgState)
        assertIs<ReadyAdvice.AlreadyConfigured>(ready.advice)
    }
}
