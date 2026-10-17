package kr.agv.cfgmanager.core.fake

import kr.agv.cfgmanager.core.util.utf8Length

/** 가짜 장치가 응답 뒤 하는 일. */
enum class FakeAction { None, Disconnect, Reboot }

/** 가짜 장치의 응답 한 덩어리. [delayMs] 뒤에 내보내고, 줄바꿈은 `\n` 으로 적는다(채널이 CRLF 로 바꿈). */
data class FakeReply(val text: String, val delayMs: Long = 0, val action: FakeAction = FakeAction.None)

/** Wi-Fi 결과 시나리오. [Down] 의 [reason] 은 `last_reason`. */
sealed interface FakeWifi {
    data object Up : FakeWifi
    data class Down(val reason: Int) : FakeWifi
}

/**
 * 브리지 설정 한 벌. IP 는 32비트 값(0 = 특수 뜻: ip 0 = dhcp, dns 0 = auto, rfid_dst 0 = off).
 * 브리지의 `app_config.c` 내부 표현을 그대로 따른다.
 */
data class FakeCfg(
    val agv: Int,
    val ssid: String,
    val pass: String,
    val ip: Long,
    val mask: Long,
    val gw: Long,
    val dns: Long,
    val dns2: Long,
    val server: Long,
    val serverPort: Int,
    val localPort: Int,
    val rfidDst: Long,
    val rfidPort: Int,
    val mainBaud: Long,
    val rfidBaud: Long,
    val hbMs: Int,
)

data class FakeDeviceOptions(
    val fw: String = "0.5.5",
    val mac: String = "a1:b2:c3:d4:a1:b2",
    val blePassword: String = "agvsetup",
    val buildDefaults: FakeCfg = FakeCfg(
        agv = 1, ssid = "AGV_TEST", pass = "agv12345", ip = 0,
        mask = parseIpLike("255.255.255.0")!!, gw = 0, dns = 0, dns2 = 0,
        server = parseIpLike("192.168.0.10")!!, serverPort = 50000, localPort = 50000,
        rfidDst = parseIpLike("255.255.255.255")!!, rfidPort = 50001,
        mainBaud = 115200, rfidBaud = 9600, hbMs = 1000,
    ),
    val nopart: Boolean = false,
    val busy: Boolean = false,
    val saveFails: Boolean = false,
    val wifi: FakeWifi = FakeWifi.Up,
    val reappearsAfterReboot: Boolean = true,
    /** help 에 덧붙일 선택 기능 명령 (예: "discard"). 펌웨어 기능 차이 시험용. */
    val helpExtras: List<String> = emptyList(),
)

/**
 * 브리지 `cmd.c` · `setup.c ble_poll()` · `app_config.c` 의 의미를 옮긴 가짜 장치.
 * 응답 문구는 docs/DEVELOPMENT_PLAN.md 부록 A 와 글자 단위로 맞춘다.
 * `set` 검증은 ConfigValidator 와 별도 구현이다(브리지 `apply()` 를 그대로 따른다, 앞자리 0 허용 포함).
 */
class FakeDevice(val options: FakeDeviceOptions = FakeDeviceOptions()) {

    /** 호기 번호 = 실행 중 설정. 장치 이름은 부팅 때 정해진다. */
    var running: FakeCfg = options.buildDefaults
        private set

    /** NVS 에 저장된 설정. null 이면 저장된 설정 없음(state=default). */
    var saved: FakeCfg? = null
        private set

    /** 편집 사본 (`set` 이 바꾸는 곳). */
    var edit: FakeCfg = options.buildDefaults
        private set

    /** BLE 광고 중인가. 저장 설정이 없으면 처음부터 열려 있다(첫 설치). */
    var setupOpen: Boolean = true
        private set

    /** 재부팅 직후 다시 나타나기까지 걸리는 중. 채널이 소비한다. */
    var bootPending: Boolean = false
        internal set

    var busy: Boolean = options.busy
    var saveFails: Boolean = options.saveFails
    var wifi: FakeWifi = options.wifi

    var saveCount: Int = 0
        private set
    var rebootCount: Int = 0
        private set

    private var authed = false
    private var failures = 0

    val name: String get() = "AGVBR-%02d-%s".format(running.agv, macSuffix())
    val fw: String get() = options.fw
    val blePassword: String get() = options.blePassword
    val loginRequired: Boolean get() = options.blePassword.isNotEmpty()

    /** 연결 직후 환영 문구 (`setup.c` ble_poll). */
    fun welcome(): String =
        "AGV Wi-Fi Bridge $name (fw ${options.fw})\n" +
            (if (loginRequired) "login <password>\n" else "type help\n")

    /** 연결이 끊기면 로그인 상태를 지운다. */
    fun onDisconnected() {
        authed = false
        failures = 0
    }

    /** 재부팅: 저장 설정(없으면 기본값)으로 다시 시작, 설정 모드는 다시 열린다(BLE reboot 뒤). */
    fun reboot() {
        rebootCount++
        running = saved ?: options.buildDefaults
        edit = running
        setupOpen = options.reappearsAfterReboot
        bootPending = true
        onDisconnected()
    }

    /** 한 줄 명령을 처리한다 (빈 줄은 무시되어 응답이 없다). */
    fun handle(rawLine: String): FakeReply {
        val line = rawLine.trimEnd('\r', '\n', ' ')
        if (line.isEmpty()) return FakeReply("")

        if (line.startsWith("login ")) return login(line.substring(6))
        if (loginRequired && !authed) return FakeReply("login required: login <password>\n")

        val cmd = line.substringBefore(' ')
        return when (cmd) {
            "cfg" -> FakeReply(cfgText())
            "set" -> setCommand(line)
            "save" -> save()
            "defaults" -> defaults()
            "reboot" -> rebootCommand()
            "stat" -> FakeReply(statText())
            "setup" -> setup(line)
            "dsp" -> dsp(line)
            "help" -> FakeReply(helpText())
            else -> FakeReply("unknown command '$cmd' - type help\n")
        }
    }

    // ---- 명령별 ----

    private fun login(password: String): FakeReply {
        if (loginRequired && password == options.blePassword) {
            authed = true
            failures = 0
            return FakeReply("ok - type help\n")
        }
        failures++
        if (failures >= MAX_LOGIN_FAILURES) {
            failures = 0
            return FakeReply(
                "wrong password - too many failures, disconnecting\n", BAD_PASS_DELAY_MS, FakeAction.Disconnect,
            )
        }
        return FakeReply("wrong password\n", BAD_PASS_DELAY_MS)
    }

    private fun setCommand(line: String): FakeReply {
        val rest = line.substring(3).trimStart(' ')
        if (rest.isEmpty()) return FakeReply(USAGE)
        val key = rest.substringBefore(' ')
        val value = if (key.length < rest.length) rest.substring(key.length).trimStart(' ') else ""
        if (value.isEmpty()) return FakeReply(USAGE)
        val updated = applySet(edit, key, value)
        return if (updated != null) {
            edit = updated
            FakeReply("ok $key - 'save' then 'reboot' to apply\n")
        } else {
            FakeReply("invalid key or value: $key\n")
        }
    }

    private fun save(): FakeReply {
        if (saveFails) return FakeReply("save failed: ESP_ERR_NVS_NOT_ENOUGH_SPACE\n")
        saved = edit
        saveCount++
        return FakeReply("saved - 'reboot' to apply\n")
    }

    private fun defaults(): FakeReply {
        saved = null
        edit = options.buildDefaults
        return FakeReply("saved config erased - 'reboot' to use build defaults\n")
    }

    private fun rebootCommand(): FakeReply {
        if (busy) {
            return FakeReply("busy: mainboard programming - reboot refused, retry when done\n")
        }
        return FakeReply("rebooting\n", 0, FakeAction.Reboot)
    }

    private fun setup(line: String): FakeReply {
        val parts = line.split(' ')
        if (parts.size < 2) return FakeReply(setupLine() + "\n")
        val open = parts[1] == "on" || parts[1] == "1"
        if (open) {
            setupOpen = true
            return FakeReply(setupLine() + "\n")
        }
        // 브리지는 'setup off' 응답에 "(closing)" 을 붙이고 곧 끊는다
        val text = "setup: on name=$name ble=connected (closing)\n"
        setupOpen = false
        return FakeReply(text, 0, FakeAction.Disconnect)
    }

    private fun dsp(line: String): FakeReply {
        val parts = line.split(' ')
        return when (parts.getOrNull(1)) {
            "ver" -> FakeReply("version query sent - 'dsp' to see\n")
            "apply" -> FakeReply("apply requested - progress goes to server (DSP_OTA_RESULT)\n")
            else -> FakeReply(dspLine() + "\n")
        }
    }

    private fun helpText(): String {
        val extras = options.helpExtras.joinToString("") { " | $it" }
        return "cfg | set <key> <value> | save | reboot | stat | setup [on|off] | dsp [ver|apply]$extras | defaults\n"
    }

    // ---- 출력 ----

    /** `cfg` (app_config.c cfg_format). 0.5.4 이하는 `dns=` 줄이 없다. */
    private fun cfgText(): String {
        val c = edit
        val sb = StringBuilder()
        sb.append("agv=${c.agv} ssid=\"${c.ssid}\" pass=(${c.pass.utf8Length()} chars)\n")
        sb.append("ip=${if (c.ip != 0L) ipText(c.ip) else "dhcp"} mask=${ipText(c.mask)} gw=${ipText(c.gw)}\n")
        if (hasDnsKeys()) {
            val dns = if (c.dns != 0L) ipText(c.dns) else "auto"
            val dns2 = if (c.dns2 != 0L) ipText(c.dns2) else "auto"
            sb.append("dns=$dns dns2=$dns2\n")
        }
        sb.append("server=${ipText(c.server)}:${c.serverPort} local_port=${c.localPort}\n")
        sb.append("rfid_dst=${if (c.rfidDst != 0L) ipText(c.rfidDst) else "off"}:${c.rfidPort}\n")
        sb.append("main_baud=${c.mainBaud} rfid_baud=${c.rfidBaud} hb_ms=${c.hbMs}\n")
        sb.append("state=${if (saved != null) "saved" else "default"}\n")
        if (edit != running) sb.append("* differs from running config - 'save' then 'reboot' to apply\n")
        return sb.toString()
    }

    /** `stat` (BLE): fw 줄, wifi · net 줄 (net_bridge.c), setup 줄, dsp 줄. */
    private fun statText(): String {
        val sb = StringBuilder()
        sb.append("fw=${options.fw} part=ota_0 ota=valid cfg=${if (saved != null) "saved" else "default"}\n")
        sb.append(wifiLine()).append('\n')
        sb.append(netLine()).append('\n')
        sb.append(setupLine()).append('\n')
        sb.append(dspLine()).append('\n')
        return sb.toString()
    }

    private fun wifiLine(): String {
        val mac = if (hasMacToken()) " mac=${options.mac}" else ""
        return when (wifi) {
            FakeWifi.Up -> {
                val ip = if (running.ip != 0L) ipText(running.ip) else DHCP_LEASE
                "wifi: UP$mac ip=$ip rssi=-55 ch=6 bssid=11:22:33:44:55:66"
            }
            is FakeWifi.Down -> "wifi: DOWN$mac ip=0.0.0.0 rssi=0 ch=0 bssid=00:00:00:00:00:00"
        }
    }

    private fun netLine(): String {
        val (disc, reason) = when (val w = wifi) {
            FakeWifi.Up -> 0 to 0
            is FakeWifi.Down -> 3 to w.reason
        }
        return "net : tx=1234 tx_err=0 tx_offline=0 rx=56 rx_bad=0 rx_foreign=0 disc=$disc last_reason=$reason"
    }

    private fun setupLine(): String = if (setupOpen) {
        "setup: on name=$name ble=connected"
    } else {
        "setup: off name=$name"
    }

    private fun dspLine(): String {
        val state = if (options.nopart) "nopart (USB reflash needed)" else "idle"
        return "dsp : app=1.0.3 boot=1.1.0 build=2026-10-01 state=$state staged=-(job 0) pan=?"
    }

    // ---- 값 규칙 (브리지 app_config.c 와 같음) ----

    private fun hasDnsKeys(): Boolean = versionAtLeast(options.fw, 0, 5, 5)

    private fun hasMacToken(): Boolean = versionAtLeast(options.fw, 0, 5, 3)

    private fun macSuffix(): String {
        val hex = options.mac.replace(":", "").uppercase()
        return hex.substring(hex.length - 4)
    }

    private companion object {
        const val MAX_LOGIN_FAILURES = 5
        const val BAD_PASS_DELAY_MS = 1000L
        const val DHCP_LEASE = "192.168.10.250"

        val USAGE = "usage: set <key> <value>\n" +
            "keys : agv ssid pass(- = none) ip(dhcp) mask gw server server_port local_port\n" +
            "       rfid_dst(off / 255.255.255.255) rfid_port main_baud rfid_baud hb_ms(0 = off, 100-2000)\n"
    }
}

/** `a.b.c.d` 를 32비트 값으로. 브리지 sscanf("%3u.%3u.%3u.%3u") 와 같이 자릿수 3 이하 · 옥텟 255 이하, 앞자리 0 허용. */
fun parseIpLike(text: String): Long? {
    val m = Regex("([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})").matchEntire(text) ?: return null
    var value = 0L
    for (i in 1..4) {
        val octet = m.groupValues[i].toInt()
        if (octet > 255) return null
        value = (value shl 8) or octet.toLong()
    }
    return value
}

internal fun ipText(v: Long): String =
    "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"

private fun versionAtLeast(fw: String, major: Int, minor: Int, patch: Int): Boolean {
    val parts = fw.split('.').map { it.toIntOrNull() ?: 0 }
    val want = listOf(major, minor, patch)
    for (i in 0..2) {
        val have = parts.getOrElse(i) { 0 }
        if (have != want[i]) return have > want[i]
    }
    return true
}

/** `set` 한 줄을 편집 사본에 적용한다. 실패하면 null. (브리지 app_config.c apply() 와 같은 규칙) */
internal fun applySet(c: FakeCfg, key: String, value: String): FakeCfg? {
    fun uint(min: Long, max: Long): Long? {
        if (!Regex("[0-9]+").matches(value)) return null
        val v = value.toLongOrNull() ?: return null
        return if (v in min..max) v else null
    }
    fun ipOrZero(zeroWord: String): Long? =
        if (value == zeroWord) 0L else parseIpLike(value)
    fun ipNonZero(): Long? = parseIpLike(value)?.takeIf { it != 0L }

    return when (key) {
        "agv" -> uint(1, 99)?.let { c.copy(agv = it.toInt()) }
        "ssid" -> if (value.isEmpty() || value.utf8Length() >= 33) null else c.copy(ssid = value)
        "pass" -> {
            val p = if (value == "-") "" else value
            val n = p.utf8Length()
            if (n != 0 && (n < 8 || n > 64)) null else c.copy(pass = p)
        }
        "ip" -> ipOrZero("dhcp")?.let { c.copy(ip = it) }
        "mask" -> ipNonZero()?.let { c.copy(mask = it) }
        "gw" -> parseIpLike(value)?.let { c.copy(gw = it) }
        "dns" -> ipOrZero("auto")?.let { c.copy(dns = it) }
        "dns2" -> ipOrZero("auto")?.let { c.copy(dns2 = it) }
        "server" -> ipNonZero()?.let { c.copy(server = it) }
        "server_port" -> uint(1, 65535)?.let { c.copy(serverPort = it.toInt()) }
        "local_port" -> uint(1, 65535)?.let { c.copy(localPort = it.toInt()) }
        "rfid_dst" -> ipOrZero("off")?.let { c.copy(rfidDst = it) }
        "rfid_port" -> uint(1, 65535)?.let { c.copy(rfidPort = it.toInt()) }
        "main_baud" -> uint(1200, 1000000)?.let { c.copy(mainBaud = it) }
        "rfid_baud" -> uint(1200, 1000000)?.let { c.copy(rfidBaud = it) }
        "hb_ms" -> uint(0, 2000)?.takeIf { it == 0L || it in 100..2000 }?.let { c.copy(hbMs = it.toInt()) }
        else -> null
    }
}

