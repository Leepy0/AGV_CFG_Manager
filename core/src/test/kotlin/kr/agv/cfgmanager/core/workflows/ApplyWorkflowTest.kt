package kr.agv.cfgmanager.core.workflows

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.bridge.Capabilities
import kr.agv.cfgmanager.core.bridge.ConfigKey
import kr.agv.cfgmanager.core.bridge.DeviceSnapshot
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.fake.FakeBridgeChannel
import kr.agv.cfgmanager.core.fake.FakeDevice
import kr.agv.cfgmanager.core.fake.FakeDeviceOptions
import kr.agv.cfgmanager.core.fake.FakeWifi
import kr.agv.cfgmanager.core.testsupport.TamperingChannel
import kr.agv.cfgmanager.core.testsupport.validTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ApplyWorkflowTest {

    /** 장치(가짜) + 채널 + 워크플로. 시간 제한은 시험에서 짧게. */
    private inner class Rig(
        val options: FakeDeviceOptions = FakeDeviceOptions(),
        wrap: (IBridgeChannel) -> IBridgeChannel = { it },
    ) {
        val device = FakeDevice(options)
        val fake = FakeBridgeChannel(device)
        val channel: IBridgeChannel = wrap(fake)
        val workflow = ApplyWorkflow(
            channel = channel,
            nowMillis = { 1_000L },
            reconnectTimeout = 5.seconds,
            wifiTimeout = 10.seconds,
            wifiPollInterval = 2.seconds,
        )

        /** 장치를 읽은 스냅샷을 만든다 (연결 · 로그인 포함). */
        suspend fun snapshot(): DeviceSnapshot {
            assertTrue(fake.waitReady(10.seconds))
            return ReadDevice(channel, nowMillis = { 1_000L }).read()
        }
    }

    private fun steps(events: List<WorkflowProgress>): List<ApplyStep> =
        events.filterIsInstance<WorkflowProgress.Step>().map { it.step }.distinct()

    @Test
    fun `정상 경로는 저장 재부팅 재연결 Wi-Fi 확인을 거쳐 Applied`() = runTest {
        val rig = Rig()
        val before = rig.snapshot()
        val target = validTarget()
        val events = mutableListOf<WorkflowProgress>()

        val result = rig.workflow.run(ApplyRequest(before, target)) { events += it }

        assertEquals(ApplyOutcome.Applied, result.outcome)
        assertEquals("ok", result.historyResult)
        assertTrue(result.saved)
        assertEquals(7, rig.device.saved?.agv)
        assertEquals(7, rig.device.running.agv)
        assertEquals(1, rig.device.rebootCount)
        assertEquals(
            listOf(
                ApplyStep.Validate, ApplyStep.SendSet, ApplyStep.ReadBack, ApplyStep.Save,
                ApplyStep.Reboot, ApplyStep.WaitReady, ApplyStep.VerifyWifi, ApplyStep.Done,
            ),
            steps(events),
        )
        assertTrue(events.any { it is WorkflowProgress.Step && it.message == "설정 전송 16/16" })
        assertIs<WorkflowProgress.Finished>(events.last())
        assertEquals("192.168.10.107", result.after?.config?.ip)
    }

    @Test
    fun `바뀐 것이 없으면 아무것도 보내지 않고 NoChange`() = runTest {
        val rig = Rig()
        val before = rig.snapshot()
        val sameAsDevice = validTarget().copy(
            agv = 1, ssid = "AGV_TEST", pass = null, passLength = null, ip = null, mask = "255.255.255.0",
            gw = "0.0.0.0", dns = null, dns2 = null, server = "192.168.0.10",
            rfidDst = "255.255.255.255", rfidPort = 50001, mainBaud = 115200, rfidBaud = 9600, hbMs = 1000,
            serverPort = 50000, localPort = 50000,
        )
        val result = rig.workflow.run(ApplyRequest(before, sameAsDevice))
        assertEquals(ApplyOutcome.NoChange, result.outcome)
        assertEquals(0, rig.device.saveCount)
        assertEquals(0, rig.device.rebootCount)
    }

    @Test
    fun `장치가 거부한 값이면 저장하지 않고 InvalidValue 로 멈춘다`() = runTest {
        val rig = Rig()
        val before = rig.snapshot()
        // 검증기를 거치지 않은 값: 장치의 apply() 가 거부하는 호기 번호 100
        val target = validTarget().copy(agv = 100)
        val result = rig.workflow.run(ApplyRequest(before, target))

        val failed = assertIs<ApplyOutcome.Failed>(result.outcome)
        assertEquals(ApplyStep.SendSet, failed.step)
        assertEquals(ApplyFailure.InvalidValue(ConfigKey.AGV, "invalid key or value: agv\r\n"), failed.failure)
        assertEquals("invalid:agv", result.historyResult)
        assertEquals(0, rig.device.saveCount, "저장이 되면 안 된다")
        assertEquals(null, rig.device.saved)
        assertEquals(0, rig.device.rebootCount)
    }

    @Test
    fun `저장 실패면 재부팅하지 않고 SaveFailed`() = runTest {
        val rig = Rig(FakeDeviceOptions(saveFails = true))
        val before = rig.snapshot()
        val result = rig.workflow.run(ApplyRequest(before, validTarget()))
        assertIs<ApplyFailure.SaveFailed>((result.outcome as ApplyOutcome.Failed).failure)
        assertEquals("save_failed", result.historyResult)
        assertEquals(false, result.saved)
        assertEquals(0, rig.device.rebootCount)
    }

    @Test
    fun `메인보드 작업 중이면 저장은 되고 재부팅만 거부된다`() = runTest {
        val rig = Rig(FakeDeviceOptions(busy = true))
        val before = rig.snapshot()
        val result = rig.workflow.run(ApplyRequest(before, validTarget()))
        val failed = assertIs<ApplyOutcome.Failed>(result.outcome)
        assertEquals(ApplyStep.Reboot, failed.step)
        assertIs<ApplyFailure.RebootRefused>(failed.failure)
        assertEquals("reboot_refused", result.historyResult)
        assertTrue(result.saved)
        assertEquals(1, rig.device.saveCount)
        assertEquals(0, rig.device.rebootCount)
    }

    @Test
    fun `재부팅 뒤 같은 보드가 다시 나타나지 않으면 ReconnectTimeout`() = runTest {
        val rig = Rig(FakeDeviceOptions(reappearsAfterReboot = false))
        val before = rig.snapshot()
        val result = rig.workflow.run(ApplyRequest(before, validTarget()))
        val failed = assertIs<ApplyOutcome.Failed>(result.outcome)
        assertEquals(ApplyStep.WaitReady, failed.step)
        assertEquals("reconnect_timeout", result.historyResult)
        assertTrue(result.saved, "저장은 완료됐다")
    }

    @Test
    fun `Wi-Fi 가 DOWN 이면 사유와 함께 저장 완료로 끝난다`() = runTest {
        val rig = Rig(FakeDeviceOptions(wifi = FakeWifi.Down(201)))
        val before = rig.snapshot()
        val result = rig.workflow.run(ApplyRequest(before, validTarget()))
        val failed = assertIs<ApplyOutcome.Failed>(result.outcome)
        assertEquals(ApplyFailure.WifiDown(201), failed.failure)
        assertEquals("wifi_down:201", result.historyResult)
        assertTrue(result.saved)
        assertEquals(7, rig.device.saved?.agv)
    }

    @Test
    fun `취소는 저장 전 SendSet 에서 되고 저장된 설정은 바뀌지 않는다`() = runTest {
        val rig = Rig()
        val before = rig.snapshot()
        val events = mutableListOf<WorkflowProgress>()
        var job: Job? = null
        job = launch {
            rig.workflow.run(ApplyRequest(before, validTarget())) { p ->
                events += p
                if (p is WorkflowProgress.Step && p.done == 5) job?.cancel()
            }
        }
        job.join()

        assertEquals(0, rig.device.saveCount)
        assertEquals(null, rig.device.saved)
        assertEquals(0, rig.device.rebootCount)
        assertEquals(7, rig.device.edit.agv, "편집 사본에는 일부 값이 남는다 (재부팅하면 사라짐)")
        val finished = assertIs<WorkflowProgress.Finished>(events.last())
        assertIs<ApplyOutcome.Cancelled>(finished.result.outcome)
        assertEquals("cancelled", finished.result.historyResult)
    }

    @Test
    fun `저장 단계에 들어가면 취소 요청은 무시되고 끝까지 적용된다`() = runTest {
        val rig = Rig()
        val before = rig.snapshot()
        var job: Job? = null
        job = launch {
            rig.workflow.run(ApplyRequest(before, validTarget())) { p ->
                if (p is WorkflowProgress.Step && p.step == ApplyStep.Save) job?.cancel()
            }
        }
        job.join()
        assertEquals(1, rig.device.saveCount)
        assertEquals(7, rig.device.saved?.agv)
        assertEquals(1, rig.device.rebootCount)
    }

    @Test
    fun `비밀번호 입력이 없으면 pass 를 보내지 않는다`() = runTest {
        val rig = Rig()
        val before = rig.snapshot()
        val target = validTarget().copy(pass = null, passLength = null)
        val events = mutableListOf<WorkflowProgress>()
        rig.workflow.run(ApplyRequest(before, target)) { events += it }
        assertTrue(events.any { it is WorkflowProgress.Step && it.message == "설정 전송 15/15" })
        assertEquals("agv12345", rig.device.running.pass, "비밀번호는 그대로 유지")
    }

    @Test
    fun `dns 를 지원하지 않는 펌웨어에서는 dns 키를 보내지 않는다`() = runTest {
        val rig = Rig(FakeDeviceOptions(fw = "0.5.4"))
        val before = rig.snapshot()
        assertEquals(false, before.capabilities.dnsKeys)
        val events = mutableListOf<WorkflowProgress>()
        val result = rig.workflow.run(ApplyRequest(before, validTarget())) { events += it }
        assertEquals(ApplyOutcome.Applied, result.outcome)
        assertTrue(events.any { it is WorkflowProgress.Step && it.message == "설정 전송 14/14" })
    }

    @Test
    fun `중간에 끊기면 다시 붙은 뒤 SendSet 부터 처음부터 다시 보낸다`() = runTest {
        var dropped = false
        val rig = Rig()
        rig.fake.dropBefore = { line ->
            if (line == "set ip 192.168.10.107" && !dropped) {
                dropped = true
                true
            } else {
                false
            }
        }
        val before = rig.snapshot()
        val events = mutableListOf<WorkflowProgress>()
        val result = rig.workflow.run(ApplyRequest(before, validTarget())) { events += it }

        assertEquals(ApplyOutcome.Applied, result.outcome)
        val firstSteps = events.filter { it is WorkflowProgress.Step && it.message == "설정 전송 1/16" }
        assertEquals(2, firstSteps.size, "전송이 처음부터 두 번 시작됐다")
        assertEquals(7, rig.device.saved?.agv)
    }

    @Test
    fun `명령 시간 초과는 한 번 더 보내고 두 번째에도 없으면 실패`() = runTest {
        val rig = Rig()
        val before = rig.snapshot()
        var calls = 0
        rig.fake.silent = { line -> line == "set agv 7" && calls++ < 1 }
        val once = rig.workflow.run(ApplyRequest(before, validTarget()))
        assertEquals(ApplyOutcome.Applied, once.outcome)

        val rig2 = Rig()
        val before2 = rig2.snapshot()
        rig2.fake.silent = { line -> line == "set agv 7" }
        val always = rig2.workflow.run(ApplyRequest(before2, validTarget()))
        val failed = assertIs<ApplyOutcome.Failed>(always.outcome)
        assertIs<ApplyFailure.Timeout>(failed.failure)
        assertEquals("timeout", always.historyResult)
        assertEquals(0, rig2.device.saveCount)
    }

    @Test
    fun `saveOnly 면 저장만 하고 재부팅하지 않는다`() = runTest {
        val rig = Rig()
        val before = rig.snapshot()
        val result = rig.workflow.run(ApplyRequest(before, validTarget(), saveOnly = true))
        assertEquals(ApplyOutcome.SavedOnly, result.outcome)
        assertEquals(1, rig.device.saveCount)
        assertEquals(0, rig.device.rebootCount)
        assertEquals(7, rig.device.saved?.agv)
    }

    @Test
    fun `연결되지 않은 상태에서는 NotReady`() = runTest {
        val device = FakeDevice()
        val channel = FakeBridgeChannel(device)
        val before = DeviceSnapshot(
            deviceId = "x", name = null, fw = null, mac = null,
            config = null, cfgState = null, status = null, capabilities = Capabilities(), readAt = 0,
        )
        val result = ApplyWorkflow(channel).run(ApplyRequest(before, validTarget()))
        val failed = assertIs<ApplyOutcome.Failed>(result.outcome)
        assertIs<ApplyFailure.NotReady>(failed.failure)
        assertEquals(0, device.saveCount)
    }

    @Test
    fun `되읽기 값이 다르면 ReadBackMismatch 로 저장하지 않는다`() = runTest {
        val rig = Rig(wrap = { inner ->
            TamperingChannel(inner) { command, reply ->
                if (command == "cfg") reply.replace("agv=7", "agv=8") else reply
            }
        })
        val before = rig.snapshot()
        val result = rig.workflow.run(ApplyRequest(before, validTarget()))
        val failed = assertIs<ApplyOutcome.Failed>(result.outcome)
        val mismatch = assertIs<ApplyFailure.ReadBackMismatch>(failed.failure)
        assertEquals(listOf(ConfigKey.AGV), mismatch.keys)
        assertEquals(0, rig.device.saveCount)
        assertEquals("readback_mismatch", result.historyResult)
    }
}
