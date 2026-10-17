package kr.agv.cfgmanager.core.workflows

import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.fake.FakeBridgeChannel
import kr.agv.cfgmanager.core.fake.FakeDevice
import kr.agv.cfgmanager.core.fake.FakeDeviceOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FactoryResetWorkflowTest {

    @Test
    fun `저장 설정을 지우고 재부팅을 요청하면 Rebooting`() = runTest {
        val device = FakeDevice()
        val channel = FakeBridgeChannel(device)
        assertTrue(channel.waitReady(10.seconds))
        val outcome = FactoryResetWorkflow.run(channel)
        assertEquals(FactoryResetOutcome.Rebooting, outcome)
        assertEquals("ok", outcome.historyResult)
    }

    @Test
    fun `메인보드 굽기 중이면 설정은 지우고 재부팅은 거부된다`() = runTest {
        val channel = FakeBridgeChannel(FakeDevice(FakeDeviceOptions(busy = true)))
        assertTrue(channel.waitReady(10.seconds))
        val outcome = FactoryResetWorkflow.run(channel)
        assertIs<FactoryResetOutcome.RebootRefused>(outcome)
        assertEquals("reboot_refused", outcome.historyResult)
    }
}
