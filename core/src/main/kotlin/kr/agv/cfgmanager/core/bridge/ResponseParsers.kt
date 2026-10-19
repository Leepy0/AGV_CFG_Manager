package kr.agv.cfgmanager.core.bridge

import kr.agv.cfgmanager.core.util.Ipv4
import kr.agv.cfgmanager.core.util.TerminalText

/**
 * 브리지 응답 파서 (docs/DEVELOPMENT_PLAN.md 3.2 · 3.2.1 · 3.3, 부록 A).
 * BLE 와 USB 콘솔 출력을 함께 읽는다. `key=value` 토큰을 읽고, 모르는 토큰은 무시한다(상위 호환).
 * 입력은 CRLF · ANSI 가 섞여도 된다.
 */
object ResponseParsers {

    data class Banner(
        val name: String,
        val fw: String,
        val needsLogin: Boolean,
        val mac: String?,
        val cfgState: CfgState?,
    )

    /** `cfg` 응답. [dnsSupported] 는 `dns=` 줄이 있었는지(0.5.5+). */
    data class CfgReport(
        val config: BridgeConfig,
        val state: CfgState,
        val differs: Boolean,
        val dnsSupported: Boolean,
    )

    sealed interface SetResult {
        data class Ok(val key: String?) : SetResult
        data class Invalid(val key: String?) : SetResult
        data object Unknown : SetResult
    }

    sealed interface SaveResult {
        data class Saved(val trial: Boolean) : SaveResult
        data class Failed(val detail: String) : SaveResult
        data object Unknown : SaveResult
    }

    sealed interface RebootResult {
        data object Rebooting : RebootResult
        data class Refused(val text: String) : RebootResult
        data object Unknown : RebootResult
    }

    sealed interface LoginResult {
        data object Ok : LoginResult
        data class WrongPassword(val tooManyFailures: Boolean) : LoginResult
        data object LoginRequired : LoginResult
        data object Unknown : LoginResult
    }

    private val passRe = Regex("pass=\\((\\d+) chars\\)")
    private val stagedRe = Regex("staged=(\\S+?)\\(job (\\d+)\\)")
    private val stateRe = Regex("state=(\\S+)")
    private val bannerRe = Regex("AGV Wi-Fi Bridge (\\S+) \\(fw ([^)\\s]+)\\)(.*)")
    private val macRe = Regex("mac=([0-9a-fA-F:]{17})")
    private val cfgRe = Regex("cfg=(\\w+)")
    private val heapRe = Regex("heap:\\s*(\\d+)")
    private val setOkRe = Regex("ok(?: ([a-z_0-9]+))? - 'save' then 'reboot' to apply")
    private val setInvalidRe = Regex("invalid key or value(?:: ([a-z_0-9]+))?")
    private val saveRe = Regex("saved(?: \\(trial\\))?(?: - 'reboot' to apply)?")
    private val saveFailRe = Regex("save failed: (.*)")

    /** 환영 문구: `AGV Wi-Fi Bridge AGVBR-07-A1B2 (fw 0.5.5)` + `login <password>` 또는 `type help`. */
    fun parseBanner(text: String): Banner? {
        val lines = clean(text)
        val head = lines.firstNotNullOfOrNull { bannerRe.find(it) } ?: return null
        val tail = head.groupValues[3]
        return Banner(
            name = head.groupValues[1],
            fw = head.groupValues[2],
            needsLogin = lines.any { it.startsWith("login ") },
            mac = macRe.find(tail)?.groupValues?.get(1)?.lowercase(),
            cfgState = cfgRe.find(tail)?.groupValues?.get(1)?.let { CfgState.fromWire(it) },
        )
    }

    /** `cfg` 응답 (부록 A-2). 필수 키가 없으면 null. */
    fun parseCfg(text: String): CfgReport? {
        val tokens = HashMap<String, String>()
        var differs = false
        var passLength: Int? = null
        for (line in clean(text)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("*")) {
                differs = true
                continue
            }
            passRe.find(trimmed)?.let { passLength = it.groupValues[1].toIntOrNull() }
            tokens.putAll(tokenize(passRe.replace(trimmed, "")))
        }
        val length = passLength ?: return null
        val agv = tokens["agv"]?.toIntOrNull() ?: return null
        val ssid = tokens["ssid"] ?: return null
        val state = tokens["state"]?.let { CfgState.fromWire(it) } ?: return null
        val mask = tokens["mask"]?.let { Ipv4.canonical(it) } ?: return null
        val gw = tokens["gw"]?.let { Ipv4.canonical(it) } ?: return null
        val ip = tokens["ip"] ?: return null
        val server = splitHostPort(tokens["server"]) ?: return null
        val localPort = tokens["local_port"]?.toIntOrNull() ?: return null
        val rfid = splitHostPort(tokens["rfid_dst"]) ?: return null
        val mainBaud = tokens["main_baud"]?.toIntOrNull() ?: return null
        val rfidBaud = tokens["rfid_baud"]?.toIntOrNull() ?: return null
        val hbMs = tokens["hb_ms"]?.toIntOrNull() ?: return null
        val config = BridgeConfig(
            agv = agv,
            ssid = ssid,
            pass = null,
            passLength = length,
            ip = if (ip == "dhcp") null else Ipv4.canonical(ip) ?: return null,
            mask = mask,
            gw = gw,
            dns = tokens["dns"]?.takeIf { it != "auto" }?.let { Ipv4.canonical(it) ?: return null },
            dns2 = tokens["dns2"]?.takeIf { it != "auto" }?.let { Ipv4.canonical(it) ?: return null },
            server = server.first,
            serverPort = server.second ?: return null,
            localPort = localPort,
            rfidDst = if (rfid.first == "off") null else rfid.first,
            rfidPort = rfid.second ?: return null,
            mainBaud = mainBaud,
            rfidBaud = rfidBaud,
            hbMs = hbMs,
        )
        return CfgReport(
            config = config,
            state = state,
            differs = differs,
            dnsSupported = tokens.containsKey("dns"),
        )
    }

    /**
     * `stat` 응답 (BLE: 첫 줄 `fw=…`, 콘솔: `main:` `rfid:` `heap:` 줄). 알 수 있는 줄이 하나도 없으면 null.
     */
    fun parseStat(text: String): BridgeStatus? {
        val lines = clean(text).map { it.trim() }
        var fw: String? = null
        var part: String? = null
        var ota: String? = null
        var cfgState: CfgState? = null
        var wifi: WifiStatus? = null
        var net: NetStatus? = null
        var setup: SetupStatus? = null
        var dsp: DspStatus? = null
        var heap: Long? = null
        val uart = LinkedHashMap<String, Map<String, Long>>()
        for (line in lines) {
            when {
                line.startsWith("fw=") -> {
                    val t = tokenize(line)
                    fw = t["fw"]
                    part = t["part"]
                    ota = t["ota"]
                    cfgState = t["cfg"]?.let { CfgState.fromWire(it) }
                }
                line.startsWith("wifi:") -> wifi = parseWifi(line)
                line.startsWith("net ") -> net = parseNet(line)
                line.startsWith("setup:") -> setup = parseSetupLine(line)
                line.startsWith("dsp ") -> dsp = parseDspLine(line)
                line.startsWith("main:") -> uart["main"] = numbers(line)
                line.startsWith("rfid:") -> uart["rfid"] = numbers(line)
                line.startsWith("heap:") -> heap = heapRe.find(line)?.groupValues?.get(1)?.toLongOrNull()
            }
        }
        if (fw == null && wifi == null && net == null && dsp == null && setup == null) return null
        return BridgeStatus(
            source = if (fw != null) StatSource.Ble else StatSource.Console,
            fw = fw,
            part = part,
            ota = ota,
            cfgState = cfgState,
            wifi = wifi,
            net = net,
            setup = setup,
            dsp = dsp,
            heapBytes = heap,
            uart = uart,
        )
    }

    /** `set` 응답: 성공 `ok <key> - 'save' ...` (콘솔은 키 없음), 실패 `invalid key or value: <key>`. */
    fun parseSetResult(text: String): SetResult {
        // 응답 앞에 다른 줄(비동기 로그 등)이 끼어도 놓치지 않게 모든 줄을 본다.
        for (raw in clean(text)) {
            val line = raw.trim()
            setOkRe.matchEntire(line)?.let { return SetResult.Ok(it.groupValues[1].ifEmpty { null }) }
            setInvalidRe.matchEntire(line)?.let { return SetResult.Invalid(it.groupValues[1].ifEmpty { null }) }
        }
        return SetResult.Unknown
    }

    /** `save` 응답: `saved - 'reboot' to apply` / `saved` / `save failed: …`. */
    fun parseSaveResult(text: String): SaveResult {
        val line = firstLine(text) ?: return SaveResult.Unknown
        if (saveRe.matches(line)) return SaveResult.Saved(trial = line.contains("(trial)"))
        saveFailRe.matchEntire(line)?.let { return SaveResult.Failed(it.groupValues[1]) }
        return SaveResult.Unknown
    }

    /** `reboot` 응답: `rebooting` 또는 메인보드 굽는 중 `busy: …`. */
    fun parseRebootResult(text: String): RebootResult {
        val line = firstLine(text) ?: return RebootResult.Unknown
        return when {
            line == "rebooting" -> RebootResult.Rebooting
            line.startsWith("busy:") -> RebootResult.Refused(line)
            else -> RebootResult.Unknown
        }
    }

    /** `login` 응답 (BLE). 여러 줄 중 첫 줄만 본다. */
    fun parseLoginResult(text: String): LoginResult {
        val line = firstLine(text) ?: return LoginResult.Unknown
        return when {
            line == "ok - type help" -> LoginResult.Ok
            line.startsWith("wrong password - too many failures") -> LoginResult.WrongPassword(true)
            line == "wrong password" -> LoginResult.WrongPassword(false)
            line.startsWith("login required") -> LoginResult.LoginRequired
            else -> LoginResult.Unknown
        }
    }

    /** `setup` · `setup on|off` 응답 한 줄 (`setup: on name=… ble=connected [(closing)]`). */
    fun parseSetup(text: String): SetupStatus? {
        val line = firstLine(text) ?: return null
        return if (line.startsWith("setup:")) parseSetupLine(line) else null
    }

    /** `dsp` 응답 한 줄 (`dsp : app=… state=… …`). */
    fun parseDsp(text: String): DspStatus? {
        val line = firstLine(text) ?: return null
        return if (line.startsWith("dsp ")) parseDspLine(line) else null
    }

    /** `help` 응답: 명령 목록(`|` 구분)에서 선택 기능 명령을 찾는다. dnsKeys 는 `cfg` 로 따로 정한다. */
    fun parseHelp(text: String): Capabilities {
        val names = clean(text)
            .flatMap { it.split('|') }
            .mapNotNull { segment -> segment.trim().split(' ').firstOrNull()?.takeIf { it.isNotEmpty() } }
            .toSet()
        return Capabilities(
            dnsKeys = false,
            discard = "discard" in names,
            scan = "scan" in names,
            nettest = "nettest" in names,
        )
    }

    // ---- 내부 ----

    private fun clean(text: String): List<String> = TerminalText.clean(text).split('\n')

    private fun firstLine(text: String): String? = clean(text).map { it.trim() }.firstOrNull { it.isNotEmpty() }

    /** `key=value` 토큰 목록. 값이 `"…"` 이면 따옴표 안의 공백을 보존한다. `=` 없는 토큰은 건너뛴다. */
    internal fun tokenize(line: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        var i = 0
        val n = line.length
        while (i < n) {
            while (i < n && line[i] == ' ') i++
            if (i >= n) break
            val start = i
            while (i < n && line[i] != '=' && line[i] != ' ') i++
            if (i >= n || line[i] == ' ') continue
            val key = line.substring(start, i)
            i++
            val value: String
            if (i < n && line[i] == '"') {
                val close = line.indexOf('"', i + 1)
                if (close < 0) {
                    value = line.substring(i + 1)
                    i = n
                } else {
                    value = line.substring(i + 1, close)
                    i = close + 1
                }
            } else {
                val valueStart = i
                while (i < n && line[i] != ' ') i++
                value = line.substring(valueStart, i)
            }
            map[key] = value
        }
        return map
    }

    /** `host:port` → (host, port). `off:50001` 처럼 host 가 off 여도 된다. 잘못되면 null. */
    private fun splitHostPort(value: String?): Pair<String, Int?>? {
        if (value == null) return null
        val colon = value.lastIndexOf(':')
        if (colon < 0) return null
        val host = value.substring(0, colon)
        val hostOk = host == "off" || Ipv4.canonical(host) != null
        if (!hostOk) return null
        return host to value.substring(colon + 1).toIntOrNull()
    }

    private fun parseWifi(line: String): WifiStatus {
        val t = tokenize(line)
        val up = line.startsWith("wifi: UP")
        return WifiStatus(
            up = up,
            mac = t["mac"]?.lowercase(),
            ip = t["ip"],
            rssi = t["rssi"]?.toIntOrNull(),
            channel = t["ch"]?.toIntOrNull(),
            bssid = t["bssid"]?.lowercase(),
        )
    }

    private fun parseNet(line: String): NetStatus {
        val t = tokenize(line)
        fun num(key: String): Long = t[key]?.toLongOrNull() ?: 0L
        return NetStatus(
            tx = num("tx"),
            txErr = num("tx_err"),
            txOffline = num("tx_offline"),
            rx = num("rx"),
            rxBad = num("rx_bad"),
            rxForeign = num("rx_foreign"),
            disconnects = num("disc"),
            lastReason = t["last_reason"]?.toIntOrNull() ?: 0,
        )
    }

    private fun parseSetupLine(line: String): SetupStatus {
        val word = line.removePrefix("setup:").trim().substringBefore(' ')
        val mode = when (word) {
            "on" -> SetupMode.On
            "off" -> SetupMode.Off
            "opening" -> SetupMode.Opening
            else -> SetupMode.Unavailable
        }
        val t = tokenize(line)
        return SetupStatus(
            mode = mode,
            name = t["name"],
            ble = t["ble"],
            apClients = t["ap_clients"]?.toIntOrNull(),
            closing = line.contains("(closing)"),
        )
    }

    private fun parseDspLine(line: String): DspStatus {
        val t = tokenize(line)
        val staged = stagedRe.find(line)
        val stagedVersion = staged?.groupValues?.get(1)
        return DspStatus(
            app = t["app"].orEmpty(),
            boot = t["boot"].orEmpty(),
            build = t["build"].orEmpty(),
            state = stateRe.find(line)?.groupValues?.get(1).orEmpty(),
            staged = stagedVersion?.takeIf { it != "-" },
            stagedJob = staged?.groupValues?.get(2)?.toLongOrNull() ?: 0L,
            pan = t["pan"].orEmpty(),
        )
    }

    private fun numbers(line: String): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for ((key, value) in tokenize(line)) {
            value.toLongOrNull()?.let { out[key] = it }
        }
        return out
    }
}
