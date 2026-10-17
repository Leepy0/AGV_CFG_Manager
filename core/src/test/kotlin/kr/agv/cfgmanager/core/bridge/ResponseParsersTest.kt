package kr.agv.cfgmanager.core.bridge

import kr.agv.cfgmanager.core.testsupport.fixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResponseParsersTest {

    @Test
    fun `환영 문구는 이름 펌웨어와 로그인 필요 여부를 읽는다`() {
        val banner = assertNotNull(ResponseParsers.parseBanner(fixture("a1_welcome_login.txt")))
        assertEquals("AGVBR-07-A1B2", banner.name)
        assertEquals("0.5.5", banner.fw)
        assertTrue(banner.needsLogin)
    }

    @Test
    fun `비밀번호 없는 빌드의 환영 문구는 type help 를 쓰고 로그인이 필요 없다`() {
        val banner = assertNotNull(ResponseParsers.parseBanner(fixture("a1_welcome_no_password.txt")))
        assertFalse(banner.needsLogin)
        assertEquals("AGVBR-01-A1B2", banner.name)
    }

    @Test
    fun `0_5_5 cfg 는 모든 키와 differs 줄을 읽는다`() {
        val report = assertNotNull(ResponseParsers.parseCfg(fixture("a2_cfg_0_5_5_differs.txt")))
        val c = report.config
        assertEquals(7, c.agv)
        assertEquals("AGV_WIFI", c.ssid)
        assertEquals(10, c.passLength)
        assertNull(c.pass)
        assertEquals("192.168.10.107", c.ip)
        assertEquals("255.255.255.0", c.mask)
        assertEquals("192.168.10.1", c.gw)
        assertEquals("192.168.10.1", c.dns)
        assertEquals("8.8.8.8", c.dns2)
        assertEquals("192.168.10.10", c.server)
        assertEquals(50000, c.serverPort)
        assertEquals(50000, c.localPort)
        assertEquals("255.255.255.255", c.rfidDst)
        assertEquals(50001, c.rfidPort)
        assertEquals(115200, c.mainBaud)
        assertEquals(9600, c.rfidBaud)
        assertEquals(1000, c.hbMs)
        assertEquals(CfgState.SAVED, report.state)
        assertTrue(report.differs)
        assertTrue(report.dnsSupported)
    }

    @Test
    fun `0_5_4 이하 cfg 는 dns 줄이 없고 dhcp 와 off 를 null 로 읽는다`() {
        val report = assertNotNull(ResponseParsers.parseCfg(fixture("a2_cfg_0_5_4_no_dns.txt")))
        assertFalse(report.dnsSupported)
        assertNull(report.config.ip)
        assertNull(report.config.rfidDst)
        assertNull(report.config.dns)
        assertEquals(CfgState.DEFAULT, report.state)
        assertFalse(report.differs)
        assertEquals(8, report.config.passLength)
    }

    @Test
    fun `공백이 든 SSID 는 따옴표 안에서 보존되고 trial 상태와 hb 0 을 읽는다`() {
        val report = assertNotNull(ResponseParsers.parseCfg(fixture("a2_cfg_trial_space_ssid.txt")))
        assertEquals("AGV WIFI 2F", report.config.ssid)
        assertEquals(CfgState.TRIAL, report.state)
        assertEquals(0, report.config.hbMs)
        assertEquals("0.0.0.0", report.config.gw)
    }

    @Test
    fun `한글 SSID 와 바이트 길이 비밀번호를 읽는다`() {
        val report = assertNotNull(ResponseParsers.parseCfg(fixture("a2_cfg_hangul_ssid.txt")))
        assertEquals("본사 1공장 WIFI", report.config.ssid)
        assertEquals(12, report.config.passLength)
        assertEquals("192.168.10.1", report.config.dns)
        assertNull(report.config.dns2)
    }

    @Test
    fun `모르는 토큰이 섞여도 cfg 를 읽는다 (상위 호환)`() {
        val text = fixture("a2_cfg_0_5_5_differs.txt").replace("state=saved", "future_key=abc state=saved")
        assertNotNull(ResponseParsers.parseCfg(text))
    }

    @Test
    fun `필수 키가 없는 cfg 는 null`() {
        assertNull(ResponseParsers.parseCfg("hello world\r\n"))
    }

    @Test
    fun `BLE stat 은 펌웨어 줄 wifi net setup dsp 를 모두 읽는다`() {
        val status = assertNotNull(ResponseParsers.parseStat(fixture("a3_stat_wifi_up.txt")))
        assertEquals(StatSource.Ble, status.source)
        assertEquals("0.5.5", status.fw)
        assertEquals("ota_0", status.part)
        assertEquals(CfgState.SAVED, status.cfgState)
        val wifi = assertNotNull(status.wifi)
        assertTrue(wifi.up)
        assertEquals("a1:b2:c3:d4:a1:b2", wifi.mac)
        assertEquals("192.168.10.107", wifi.ip)
        assertEquals(-55, wifi.rssi)
        assertEquals(6, wifi.channel)
        assertEquals(0, assertNotNull(status.net).lastReason)
        assertEquals(SetupMode.On, assertNotNull(status.setup).mode)
        assertEquals("AGVBR-07-A1B2", status.setup?.name)
        assertEquals("connected", status.setup?.ble)
        assertEquals("1.0.3", assertNotNull(status.dsp).app)
        assertFalse(status.dsp!!.isNoPartition)
    }

    @Test
    fun `Wi-Fi DOWN 이면 up 이 false 이고 끊김 사유 201 을 읽는다`() {
        val status = assertNotNull(ResponseParsers.parseStat(fixture("a3_stat_wifi_down_201.txt")))
        assertFalse(assertNotNull(status.wifi).up)
        assertEquals("0.0.0.0", status.wifi?.ip)
        assertEquals(201, assertNotNull(status.net).lastReason)
        assertEquals(3, status.net?.disconnects)
    }

    @Test
    fun `0_5_2 stat 은 mac 토큰이 없고 mac 은 null, setup 은 off`() {
        val status = assertNotNull(ResponseParsers.parseStat(fixture("a3_stat_old_0_5_2.txt")))
        assertNull(status.wifi?.mac)
        assertTrue(status.wifi?.up == true)
        assertEquals(SetupMode.Off, status.setup?.mode)
        assertEquals(8, status.net?.lastReason)
    }

    @Test
    fun `구 파티션 보드는 dsp 상태 nopart 로 읽는다`() {
        val status = assertNotNull(ResponseParsers.parseStat(fixture("a3_stat_nopart.txt")))
        assertTrue(assertNotNull(status.dsp).isNoPartition)
    }

    @Test
    fun `콘솔 stat 은 fw 가 없고 main rfid heap 줄을 읽는다`() {
        val status = assertNotNull(ResponseParsers.parseStat(fixture("a3_stat_console.txt")))
        assertEquals(StatSource.Console, status.source)
        assertNull(status.fw)
        assertEquals(120L, status.uart["main"]?.get("rx_frm"))
        assertEquals(3L, status.uart["rfid"]?.get("rx_frm"))
        assertEquals(212345L, status.heapBytes)
    }

    @Test
    fun `콘솔 로그 줄과 ANSI 색 코드는 무시하고 프롬프트 앞까지 읽는다`() {
        val status = assertNotNull(ResponseParsers.parseStat(fixture("console_with_logs_and_ansi.txt")))
        assertEquals(StatSource.Console, status.source)
        assertTrue(assertNotNull(status.wifi).up)
        assertEquals(SetupMode.Off, status.setup?.mode)
    }

    @Test
    fun `set 응답은 BLE 의 키 이름과 콘솔의 키 없는 성공 문구를 모두 읽는다`() {
        val bleOk = ResponseParsers.parseSetResult("ok ssid - 'save' then 'reboot' to apply\r\n")
        assertEquals(ResponseParsers.SetResult.Ok("ssid"), bleOk)
        assertEquals(ResponseParsers.SetResult.Ok(null), ResponseParsers.parseSetResult(fixture("console_set_ok.txt")))
        val bleInvalid = ResponseParsers.parseSetResult("invalid key or value: agv\r\n")
        assertEquals(ResponseParsers.SetResult.Invalid("agv"), bleInvalid)
        val consoleInvalid = ResponseParsers.parseSetResult(fixture("console_set_invalid.txt"))
        assertEquals(ResponseParsers.SetResult.Invalid(null), consoleInvalid)
        assertEquals(ResponseParsers.SetResult.Unknown, ResponseParsers.parseSetResult("usage: set <key> <value>\r\n"))
    }

    @Test
    fun `save 응답은 저장 성공과 실패를 구분한다`() {
        val bleSaved = ResponseParsers.parseSaveResult("saved - 'reboot' to apply\r\n")
        assertEquals(ResponseParsers.SaveResult.Saved(false), bleSaved)
        assertEquals(ResponseParsers.SaveResult.Saved(false), ResponseParsers.parseSaveResult("saved\n"))
        val trial = ResponseParsers.parseSaveResult("saved (trial) - 'reboot' to apply\n")
        assertEquals(ResponseParsers.SaveResult.Saved(true), trial)
        val failed = ResponseParsers.parseSaveResult("save failed: ESP_ERR_NVS_NOT_ENOUGH_SPACE\r\n")
        assertEquals(ResponseParsers.SaveResult.Failed("ESP_ERR_NVS_NOT_ENOUGH_SPACE"), failed)
    }

    @Test
    fun `reboot 응답은 재부팅과 메인보드 굽는 중 거부를 구분한다`() {
        assertEquals(ResponseParsers.RebootResult.Rebooting, ResponseParsers.parseRebootResult("rebooting\r\n"))
        val refused = ResponseParsers.parseRebootResult(fixture("a4_reboot_busy.txt"))
        assertIs<ResponseParsers.RebootResult.Refused>(refused)
    }

    @Test
    fun `login 응답은 성공 · 틀림 · 다섯 번 실패 · 로그인 필요를 구분한다`() {
        assertEquals(ResponseParsers.LoginResult.Ok, ResponseParsers.parseLoginResult(fixture("a1_login_ok.txt")))
        assertEquals(
            ResponseParsers.LoginResult.WrongPassword(false),
            ResponseParsers.parseLoginResult(fixture("a1_login_wrong.txt")),
        )
        assertEquals(
            ResponseParsers.LoginResult.WrongPassword(true),
            ResponseParsers.parseLoginResult(fixture("a1_login_too_many.txt")),
        )
        val required = ResponseParsers.parseLoginResult(fixture("a1_login_required.txt"))
        assertEquals(ResponseParsers.LoginResult.LoginRequired, required)
    }

    @Test
    fun `setup 응답은 열림 · 닫는 중 상태를 읽는다`() {
        val lines = fixture("a5_setup_help_unknown.txt").split("\r\n")
        val opened = assertNotNull(ResponseParsers.parseSetup(lines[0]))
        assertEquals(SetupMode.On, opened.mode)
        assertFalse(opened.closing)
        val closing = assertNotNull(ResponseParsers.parseSetup(lines[1]))
        assertTrue(closing.closing)
        assertEquals("AGVBR-07-A1B2", closing.name)
        assertNull(ResponseParsers.parseSetup("help text"))
    }

    @Test
    fun `dsp 줄은 스테이징 버전과 작업 번호를 읽고 - 는 없음으로 본다`() {
        val idleLine = "dsp : app=1.0.3 boot=1.1.0 build=2026-10-01 state=idle staged=-(job 0) pan=?\r\n"
        val idle = assertNotNull(ResponseParsers.parseDsp(idleLine))
        assertNull(idle.staged)
        assertEquals("idle", idle.state)
        assertEquals("?", idle.pan)
        val stagedLine = "dsp : app=1.0.3 boot=1.1.0 build=x state=staged staged=0.9.1(job 3) pan=ok\r\n"
        val staged = assertNotNull(ResponseParsers.parseDsp(stagedLine))
        assertEquals("0.9.1", staged.staged)
        assertEquals(3L, staged.stagedJob)
    }

    @Test
    fun `help 응답에서 선택 기능 명령을 찾는다`() {
        val base = ResponseParsers.parseHelp(
            "cfg | set <key> <value> | save | reboot | stat | setup [on|off] | dsp [ver|apply] | defaults\r\n",
        )
        assertFalse(base.discard)
        assertFalse(base.scan)
        assertFalse(base.nettest)
        assertFalse(base.dnsKeys)

        val extended = ResponseParsers.parseHelp(
            "cfg | set <key> <value> | save | reboot | stat | discard | scan | nettest | defaults\r\n",
        )
        assertTrue(extended.discard)
        assertTrue(extended.scan)
        assertTrue(extended.nettest)
    }

    @Test
    fun `토큰 모양이 맞지 않는 줄은 건너뛰고 나머지는 읽는다`() {
        val status = assertNotNull(
            ResponseParsers.parseStat("fw=0.5.5 part=ota_0 ota=valid cfg=saved junk without equal\r\n" +
                "wifi: UP mac=a1:b2:c3:d4:a1:b2 ip=192.168.10.107 rssi=-55 ch=6 bssid=11:22:33:44:55:66\r\n"),
        )
        assertEquals("0.5.5", status.fw)
        assertTrue(assertNotNull(status.wifi).up)
    }
}
