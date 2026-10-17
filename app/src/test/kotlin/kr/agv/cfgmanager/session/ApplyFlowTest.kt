package kr.agv.cfgmanager.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.apply.ApplyRunner
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.core.bridge.BridgeConfig
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.fake.FakeDevice
import kr.agv.cfgmanager.core.fake.FakeDeviceOptions
import kr.agv.cfgmanager.core.fake.FakeWifi
import kr.agv.cfgmanager.core.workflows.ApplyFailure
import kr.agv.cfgmanager.core.workflows.ApplyOutcome
import kr.agv.cfgmanager.core.workflows.ApplyStep
import kr.agv.cfgmanager.data.HistoryStore
import kr.agv.cfgmanager.ui.config.ConfigDraft
import kr.agv.cfgmanager.ui.config.ConfigForm
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 세션 잠금 · 가짜 장치로 적용 전 흐름을 돌린다 (하드웨어 없음). */
@OptIn(ExperimentalCoroutinesApi::class)
class ApplyFlowTest {

    private val address = "00:AB:CD:00:00:01"

    private inner class Rig(scope: TestScope, options: FakeDeviceOptions = FakeDeviceOptions()) {
        val device = FakeDevice(options)
        val boards = FakeBoardSource(listOf(FakeBoardEntry(address, device, rssi = -50)), clock = { 0L })
        val session = BridgeSession(boards, scope.backgroundScope, clock = { 0L })
        private val dir: File = Files.createTempDirectory("apply-flow").toFile()
        val history = HistoryStore(File(dir, "history.jsonl"))
        val runner = ApplyRunner(session, history, scope, clock = { 1_700_000_000_000L }, isFake = { true })

        fun close() {
            dir.deleteRecursively()
        }
    }

    /** 연결 · 읽기 뒤 장치 값으로 초안을 만든다. */
    private suspend fun Rig.connectDraft(): ConfigDraft {
        assertIs<ConnectOutcome.Connected>(session.connect(address, null, "agvsetup"))
        return ConfigForm.fromDevice(session.state.value.snapshot!!.config!!)
    }

    @Test
    fun `정상 적용은 저장 재부팅 재연결 Wi-Fi 확인을 거쳐 완료되고 이력에 남는다`() = runTest {
        val rig = Rig(this)
        try {
            val draft = rig.connectDraft().with(ConfigKey.AGV, "8").with(ConfigKey.SERVER, "192.168.10.20")
            val target = ConfigForm.check(draft).config!!
            assertTrue(rig.runner.start(target, saveOnly = false))

            val done = rig.runner.state.first { it is ApplyUi.Done } as ApplyUi.Done
            assertEquals(ApplyOutcome.Applied, done.result.outcome)
            assertEquals(1, rig.device.saveCount)
            assertEquals(1, rig.device.rebootCount)
            assertEquals(8, rig.session.state.value.snapshot?.config?.agv)
            assertEquals(LinkStatus.Connected, rig.session.state.value.link)
            assertEquals(listOf("ok"), rig.history.readAll().map { it.result })
            assertEquals(8, rig.history.readAll().single().agv)
        } finally {
            rig.close()
        }
    }

    @Test
    fun `장치가 값을 거부하면 저장하지 않고 멈추며 이력에 invalid 로 남는다`() = runTest {
        val rig = Rig(this)
        try {
            val target = rig.connectDraft().let { ConfigForm.check(it.with(ConfigKey.AGV, "8")).config!! }
                .copy(server = "0.0.0.0")
            rig.runner.start(target, saveOnly = false)

            val done = rig.runner.state.first { it is ApplyUi.Done } as ApplyUi.Done
            val outcome = assertIs<ApplyOutcome.Failed>(done.result.outcome)
            assertEquals(ApplyStep.SendSet, outcome.step)
            val failure = assertIs<ApplyFailure.InvalidValue>(outcome.failure)
            assertEquals(ConfigKey.SERVER, failure.key)
            assertEquals(0, rig.device.saveCount)
            assertEquals(0, rig.device.rebootCount)
            assertEquals(listOf("invalid:server"), rig.history.readAll().map { it.result })
        } finally {
            rig.close()
        }
    }

    @Test
    fun `메인보드 작업 중이면 저장은 되고 재부팅만 거부된다`() = runTest {
        val rig = Rig(this, FakeDeviceOptions(busy = true))
        try {
            val target = ConfigForm.check(rig.connectDraft().with(ConfigKey.AGV, "8")).config!!
            rig.runner.start(target, saveOnly = false)

            val done = rig.runner.state.first { it is ApplyUi.Done } as ApplyUi.Done
            val outcome = assertIs<ApplyOutcome.Failed>(done.result.outcome)
            assertEquals(ApplyStep.Reboot, outcome.step)
            assertIs<ApplyFailure.RebootRefused>(outcome.failure)
            assertTrue(done.result.saved)
            assertEquals(1, rig.device.saveCount)
            assertEquals(0, rig.device.rebootCount)
            assertEquals(listOf("reboot_refused"), rig.history.readAll().map { it.result })
        } finally {
            rig.close()
        }
    }

    @Test
    fun `재부팅 뒤 Wi-Fi 가 내려가 있으면 사유와 함께 저장 완료로 끝난다`() = runTest {
        val rig = Rig(this, FakeDeviceOptions(wifi = FakeWifi.Down(201)))
        try {
            val target = ConfigForm.check(rig.connectDraft().with(ConfigKey.AGV, "8")).config!!
            rig.runner.start(target, saveOnly = false)

            val done = rig.runner.state.first { it is ApplyUi.Done } as ApplyUi.Done
            val outcome = assertIs<ApplyOutcome.Failed>(done.result.outcome)
            assertEquals(ApplyStep.VerifyWifi, outcome.step)
            assertEquals(201, assertIs<ApplyFailure.WifiDown>(outcome.failure).reason)
            assertTrue(done.result.saved)
            assertEquals(listOf("wifi_down:201"), rig.history.readAll().map { it.result })
        } finally {
            rig.close()
        }
    }

    @Test
    fun `적용 중에는 콘솔 명령과 다른 보드 연결이 막히고 끝나면 다시 된다`() = runTest {
        val rig = Rig(this)
        try {
            rig.connectDraft()
            rig.session.withExclusive { channel ->
                assertTrue(rig.session.sendConsole("stat").isFailure)
                assertIs<ConnectOutcome.Failed>(rig.session.connect(address, null, "agvsetup"))
                assertTrue(channel.exec("stat").contains("fw="))
            }
            assertTrue(rig.session.sendConsole("stat").isSuccess)
        } finally {
            rig.close()
        }
    }

    @Test
    fun `저장 전 단계에서 취소하면 저장되지 않고 취소로 끝난다`() = runTest {
        val rig = Rig(this)
        try {
            val target = ConfigForm.check(rig.connectDraft().with(ConfigKey.AGV, "8")).config!!
            rig.runner.start(target, saveOnly = false)
            rig.runner.cancel()

            val done = rig.runner.state.first { it is ApplyUi.Done } as ApplyUi.Done
            assertIs<ApplyOutcome.Cancelled>(done.result.outcome)
            assertEquals(0, rig.device.saveCount)
            assertEquals(listOf("cancelled"), rig.history.readAll().map { it.result })
        } finally {
            rig.close()
        }
    }

    @Test
    fun `연결이 없으면 적용을 시작하지 않고 준비 안 됨으로 끝난다`() = runTest {
        val rig = Rig(this)
        try {
            val target = BridgeConfig(
                agv = 8, ssid = "AGV_TEST", pass = null, passLength = null, ip = null,
                mask = "255.255.255.0", gw = "192.168.0.1", dns = null, dns2 = null,
                server = "192.168.0.10", serverPort = 50000, localPort = 50000, rfidDst = null,
                rfidPort = 50001, mainBaud = 115200, rfidBaud = 9600, hbMs = 1000,
            )
            rig.runner.start(target, saveOnly = false)
            val done = rig.runner.state.first { it is ApplyUi.Done } as ApplyUi.Done
            val outcome = assertIs<ApplyOutcome.Failed>(done.result.outcome)
            assertIs<ApplyFailure.NotReady>(outcome.failure)
            assertEquals(0, rig.device.saveCount)
        } finally {
            rig.close()
        }
    }
}
