package kr.agv.cfgmanager.apply

import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.fake.FakeBridgeChannel
import kr.agv.cfgmanager.core.fake.FakeDevice
import kr.agv.cfgmanager.core.fake.FakeDeviceOptions
import kr.agv.cfgmanager.core.workflows.FactoryResetOutcome
import kr.agv.cfgmanager.data.HistoryStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FactoryResetRunnerTest {

    private lateinit var dir: File
    private lateinit var history: HistoryStore

    private val target = ResetTarget(agv = 7, bleAddress = "AA:BB:CC:DD:EE:FF", mac = "a1:b2:c3:d4:a1:b2", deviceName = "AGVBR-07-A1B2", fw = "0.5.5")

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("reset-test").toFile()
        history = HistoryStore(File(dir, "history.jsonl"))
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private suspend fun readyChannel(device: FakeDevice = FakeDevice()): FakeBridgeChannel {
        val channel = FakeBridgeChannel(device)
        assertTrue(channel.waitReady(10.seconds))
        return channel
    }

    /** 세션의 독점 구간을 흉내 낸다: 준비된 채널을 한 번 넘긴다. */
    private fun runnerFor(channel: IBridgeChannel?) = FactoryResetRunner(
        exclusive = { block ->
            block(channel ?: throw BridgeChannelException.NotConnected())
        },
        history = history,
        clock = { 1_700_000_000_000L },
    )

    @Test
    fun `defaults 와 reboot 를 보내고 이력에 defaults 로 남긴다`() = runTest {
        val channel = readyChannel()
        val run = runnerFor(channel).run(target)
        assertEquals(FactoryResetOutcome.Rebooting, run.outcome)
        assertEquals(false, run.historyFailed)
        val entry = history.readAll().single()
        assertEquals("defaults", entry.action)
        assertEquals("ok", entry.result)
        assertEquals(7, entry.agv)
        assertEquals("AGVBR-07-A1B2", entry.deviceName)
    }

    @Test
    fun `메인보드 굽기 중이면 재부팅 거부를 이력에 남긴다`() = runTest {
        val channel = readyChannel(FakeDevice(FakeDeviceOptions(busy = true)))
        val run = runnerFor(channel).run(target)
        assertIs<FactoryResetOutcome.RebootRefused>(run.outcome)
        assertEquals("reboot_refused", history.readAll().single().result)
    }

    @Test
    fun `연결이 없으면 아무것도 보내지 않고 이력도 남기지 않는다`() = runTest {
        val run = runnerFor(null).run(target)
        assertIs<FactoryResetOutcome.EraseFailed>(run.outcome)
        assertTrue(history.readAll().isEmpty())
    }
}
