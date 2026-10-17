package kr.agv.cfgmanager.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.core.bridge.BridgeChannelException
import kr.agv.cfgmanager.core.bridge.BridgeCommands
import kr.agv.cfgmanager.core.bridge.BridgeStatus
import kr.agv.cfgmanager.core.bridge.ChannelKind
import kr.agv.cfgmanager.core.bridge.ChannelState
import kr.agv.cfgmanager.core.bridge.DeviceSnapshot
import kr.agv.cfgmanager.core.bridge.IBridgeChannel
import kr.agv.cfgmanager.core.bridge.ResponseParsers
import kr.agv.cfgmanager.core.util.utf8Length
import kr.agv.cfgmanager.core.workflows.ReadDevice
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 연결 시도 결과. */
sealed interface ConnectOutcome {
    data class Connected(val snapshot: DeviceSnapshot) : ConnectOutcome
    data class Failed(val failure: ConnectFailure) : ConnectOutcome
}

/** 재부팅 요청 결과. [Refused] 는 메인보드 작업 중이라 거부된 것이다 (연결은 그대로). */
sealed interface RebootOutcome {
    data class Rebooting(val text: String) : RebootOutcome
    data class Refused(val text: String) : RebootOutcome
    data class Failed(val failure: Throwable) : RebootOutcome
}

/**
 * 앱 수준 연결 세션 (docs/DEVELOPMENT_PLAN.md 6.4). 화면이 바뀌어도, 회전해도 연결을 쥐고 있다.
 * - 명령은 한 번에 하나(세션 [lock] + 채널의 명령 잠금).
 * - 적용 워크플로는 [withExclusive] 안에서 돈다. 그 동안에는 자동 재연결 · 읽기 · 콘솔 · 연결 전환을 막는다.
 *   재연결 · 읽기는 워크플로가 맡는다(WaitReady · VerifyWifi · 끝의 읽기), 세션은 같은 명령을 중복으로 보내지 않는다.
 * - 예기치 않은 끊김·재부팅은 30초 동안 주소로 다시 연결한다 (F-C5).
 * - 5분 동안 사용이 없으면 끊는다.
 */
class BridgeSession(
    private val boards: BoardSource,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** 연결 · 끊김 · 재연결 같은 큰 사건을 앱 로그에 남긴다. 명령 원문은 넘기지 않는다. */
    private val journal: (String) -> Unit = {},
) {
    private val stateFlow = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = stateFlow.asStateFlow()

    private val log = ConsoleLog()
    private val consoleFlow = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val console: StateFlow<List<ConsoleLine>> = consoleFlow.asStateFlow()

    private val lock = Mutex()
    private var active: IBridgeChannel? = null
    private var logged: IBridgeChannel? = null
    private var target: Target? = null
    private var userClosed = false
    private var connectJob: Job? = null
    private var rawJob: Job? = null
    private var watchJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastActivityMs = clock()
    private val wrongPasswordCounts = HashMap<String, Int>()

    /** [withExclusive] 가 도는 동안 true. 호출 스레드(메인)에서만 읽고 쓴다. */
    private var workflowRunning = false

    private data class Target(val address: String, val name: String?, val password: String)

    private sealed interface LinkResult {
        data class Linked(val channel: IBridgeChannel) : LinkResult
        data class Failed(val failure: ConnectFailure) : LinkResult
    }

    init {
        scope.launch {
            while (isActive) {
                delay(IDLE_CHECK_MS.milliseconds)
                checkIdle()
            }
        }
    }

    /** 스캔 중 표시용. 연결 중이면 칩은 연결 상태를 우선한다. */
    fun setScanning(scanning: Boolean) {
        stateFlow.update { it.copy(scanning = scanning) }
    }

    /**
     * 주소의 보드에 연결하고 로그인한 뒤 장치를 읽는다. 호출을 취소하면 연결을 정리하고 끊김으로 둔다.
     */
    suspend fun connect(
        address: String,
        name: String?,
        password: String,
        onStage: (ConnectStage) -> Unit = {},
    ): ConnectOutcome {
        if (workflowRunning) return ConnectOutcome.Failed(ConnectFailure.Other("적용 중이라 다른 보드에 연결할 수 없습니다"))
        connectJob = currentCoroutineContext()[Job]
        userClosed = false
        journal("연결 시도")
        return try {
            lock.withLock {
                dropLinkLocked(LinkStatus.Disconnected)
                target = Target(address, name, password)
                stateFlow.update {
                    SessionState(link = LinkStatus.Connecting, address = address, name = name, scanning = it.scanning)
                }
                touch()
                when (val link = linkLocked(Target(address, name, password), CONNECT_TIMEOUT, onStage)) {
                    is LinkResult.Failed -> {
                        stateFlow.update { it.copy(link = LinkStatus.Disconnected) }
                        val failed = countFailure(address, link.failure)
                        journal("연결 실패: ${failed::class.simpleName}")
                        ConnectOutcome.Failed(failed)
                    }
                    is LinkResult.Linked -> readLocked(link.channel, address, onStage)
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                lock.withLock { dropLinkLocked(LinkStatus.Disconnected) }
            }
            throw cancelled
        } catch (unexpected: Exception) {
            // 채널이 예상 밖의 예외를 던져도 앱이 죽지 않게 연결을 정리하고 실패로 돌려준다.
            withContext(NonCancellable) {
                lock.withLock { dropLinkLocked(LinkStatus.Disconnected, notice = "연결 중 문제가 생겼습니다. 다시 시도하세요.") }
            }
            ConnectOutcome.Failed(ConnectFailure.Other("블루투스 연결 중 오류가 났습니다"))
        } finally {
            connectJob = null
        }
    }

    /** 사용자가 끊는다. 자동 재연결도 멈춘다. [notice] 는 칩 아래 안내로 남는다. 적용 중에는 무시한다. */
    suspend fun disconnect(notice: String? = null) {
        if (workflowRunning) return
        userClosed = true
        journal("사용자가 연결을 끊음")
        reconnectJob?.cancel()
        reconnectJob = null
        connectJob?.cancel()
        lock.withLock {
            dropLinkLocked(LinkStatus.Disconnected, notice)
        }
    }

    /**
     * 상태 줄(`stat`)만 다시 읽는다. 진단 탭의 [새로고침]과 자동 5초가 쓴다.
     * [auto] 이면 사용자 활동으로 치지 않는다 (유휴 끊김 타이머를 막지 않는다).
     */
    suspend fun refreshStatus(auto: Boolean = false): Result<BridgeStatus> {
        if (workflowRunning) return Result.failure(IllegalStateException("적용 중입니다"))
        return lock.withLock {
            val channel = logged ?: return@withLock Result.failure(BridgeChannelException.NotConnected())
            if (!auto) touch()
            try {
                val text = channel.exec(BridgeCommands.stat())
                val status = ResponseParsers.parseStat(text)
                    ?: return@withLock Result.failure(IllegalStateException("stat 응답을 해석하지 못했습니다"))
                stateFlow.update { st ->
                    st.copy(snapshot = st.snapshot?.copy(status = status, readAt = clock()))
                }
                Result.success(status)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: BridgeChannelException) {
                handleChannelFailure(failure)
                Result.failure(failure)
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
    }

    /**
     * 콘솔 입력 한 줄을 보낸다. 응답 원문은 [console] 에 쌓인다.
     * `reboot` 와 `setup off` 는 줄 자체로 상태를 바꾸지 않고 [reboot] · [closeSetupMode] 로 넘긴다.
     */
    suspend fun sendConsole(line: String): Result<String> {
        val command = line.trim()
        if (command.isEmpty()) return Result.failure(IllegalArgumentException("명령을 입력하세요"))
        if (command.utf8Length() > BridgeCommands.BLE_LINE_MAX_BYTES) {
            return Result.failure(IllegalArgumentException("명령이 너무 깁니다 (BLE 한 줄은 ${BridgeCommands.BLE_LINE_MAX_BYTES}바이트까지)"))
        }
        if (workflowRunning) return Result.failure(IllegalStateException("적용 중에는 콘솔을 쓸 수 없습니다"))
        val words = command.split(' ')
        if (words.first() == "reboot") {
            return when (val outcome = reboot()) {
                is RebootOutcome.Rebooting -> Result.success(outcome.text)
                is RebootOutcome.Refused -> Result.success(outcome.text)
                is RebootOutcome.Failed -> Result.failure(outcome.failure)
            }
        }
        if (words.take(2) == listOf("setup", "off")) return closeSetupMode()
        touch()
        return lock.withLock {
            val channel = logged ?: return@withLock Result.failure(BridgeChannelException.NotConnected())
            try {
                Result.success(channel.exec(command))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: BridgeChannelException) {
                handleChannelFailure(failure)
                Result.failure(failure)
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
    }

    /** 재부팅 요청(`reboot`). 응답이 `rebooting` 이면 같은 주소로 다시 붙어 장치를 읽는다 (F-C5). */
    suspend fun reboot(): RebootOutcome {
        if (workflowRunning) return RebootOutcome.Failed(IllegalStateException("적용 중입니다"))
        touch()
        return lock.withLock {
            val channel = logged ?: return@withLock RebootOutcome.Failed(BridgeChannelException.NotConnected())
            try {
                val text = channel.execThenDisconnect(BridgeCommands.reboot())
                when (ResponseParsers.parseRebootResult(text)) {
                    ResponseParsers.RebootResult.Rebooting -> {
                        journal("재부팅 요청 응답")
                        stateFlow.update { it.copy(link = LinkStatus.Rebooting, snapshot = null) }
                        RebootOutcome.Rebooting(text)
                    }
                    is ResponseParsers.RebootResult.Refused -> RebootOutcome.Refused(text)
                    ResponseParsers.RebootResult.Unknown ->
                        RebootOutcome.Failed(IllegalStateException("재부팅 응답을 해석하지 못했습니다"))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: BridgeChannelException) {
                handleChannelFailure(failure)
                RebootOutcome.Failed(failure)
            } catch (failure: Exception) {
                RebootOutcome.Failed(failure)
            }
        }
    }

    /** 설정 모드를 닫는다(`setup off`). 보드는 곧 광고를 멈추므로 자동 재연결하지 않는다. */
    suspend fun closeSetupMode(): Result<String> {
        if (workflowRunning) return Result.failure(IllegalStateException("적용 중입니다"))
        userClosed = true
        reconnectJob?.cancel()
        reconnectJob = null
        touch()
        return lock.withLock {
            val channel = logged ?: return@withLock Result.failure(BridgeChannelException.NotConnected())
            try {
                val text = channel.exec(BridgeCommands.setup(false))
                userClosed = true
                dropLinkLocked(LinkStatus.Disconnected, "설정 모드를 닫았습니다. 보드는 Wi-Fi 연결 상태로 동작합니다.")
                Result.success(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: BridgeChannelException) {
                handleChannelFailure(failure)
                Result.failure(failure)
            } catch (failure: Exception) {
                Result.failure(failure)
            }
        }
    }

    /**
     * 여러 명령을 끼어들기 없이 보내야 할 때(적용 워크플로) 쓴다. [block] 이 도는 동안 세션 잠금을 쥐고,
     * 자동 재연결 · 읽기 · 콘솔 · 다른 보드 연결을 막는다. 연결이 없으면 [BridgeChannelException.NotConnected].
     * 끝나면 연결이 살아 있으면 Connected 로 두고, 끊겨 있으면 주소로 다시 연결을 시작한다.
     */
    suspend fun <T> withExclusive(block: suspend (IBridgeChannel) -> T): T = lock.withLock {
        val channel = logged ?: throw BridgeChannelException.NotConnected()
        touch()
        workflowRunning = true
        try {
            block(channel)
        } finally {
            workflowRunning = false
            // 이 블록은 취소될 수 있으므로 suspend 호출 없이 상태만 맞춘다.
            if (active?.state?.value is ChannelState.Connected) {
                stateFlow.update { if (it.link == LinkStatus.Connected) it else it.copy(link = LinkStatus.Connected, reconnectLeftSec = null) }
            } else if (!userClosed && active != null) {
                startReconnect()
            }
        }
    }

    /** 워크플로가 읽은 최신 장치 상태를 세션에 반영한다 (화면 · 칩 · 다음 적용의 기준). */
    fun adoptSnapshot(snapshot: DeviceSnapshot) {
        stateFlow.update {
            it.copy(snapshot = snapshot, name = snapshot.name ?: it.name, fw = snapshot.fw ?: it.fw)
        }
    }

    // ---- 내부: 연결 · 읽기 (lock 을 잡은 상태에서 호출) ----

    /** 채널을 만들고 `waitReady` 를 부른다. 성공하면 원문 수집과 끊김 감시를 붙인다. */
    private suspend fun linkLocked(t: Target, timeout: Duration, onStage: (ConnectStage) -> Unit): LinkResult {
        val raw = try {
            boards.channel(t.address, t.name, t.password, onStage)
        } catch (failure: Exception) {
            return LinkResult.Failed(ConnectFailure.Other(failure.message ?: "블루투스를 사용할 수 없습니다"))
        }
        log.system("연결 시도: ${t.address}", clock())
        publishConsole()
        rawJob = scope.launch {
            raw.raw.collect { text ->
                log.received(text, clock())
                publishConsole()
            }
        }
        // 연결 안의 로그인은 채널 내부에서 일어나므로, 같은 형식으로 콘솔에 남긴다 (값은 가려진다). USB 는 로그인이 없다.
        if (raw.kind != ChannelKind.UsbSerial) {
            log.sent(BridgeCommands.login(t.password), clock())
            publishConsole()
        }
        val ready = try {
            raw.waitReady(timeout)
        } catch (cancelled: CancellationException) {
            rawJob?.cancel()
            rawJob = null
            throw cancelled
        } catch (unexpected: Exception) {
            rawJob?.cancel()
            rawJob = null
            return LinkResult.Failed(ConnectFailure.Other("블루투스 연결을 시작하지 못했습니다"))
        }
        if (!ready) {
            rawJob?.cancel()
            rawJob = null
            val failure = (raw as? LinkReport)?.failure ?: ConnectFailure.Timeout
            return LinkResult.Failed(failure)
        }
        active = raw
        logged = LoggedChannel(raw, log, clock, onChange = ::publishConsole)
        watch(raw)
        return LinkResult.Linked(raw)
    }

    /** 장치 읽기 (`cfg` → `stat` → `help`). 실패하면 연결을 끊는다. */
    private suspend fun readLocked(channel: IBridgeChannel, address: String, onStage: (ConnectStage) -> Unit): ConnectOutcome {
        onStage(ConnectStage.Read)
        val wrapped = logged ?: channel
        return try {
            val snapshot = ReadDevice(wrapped, clock).read()
            wrongPasswordCounts.remove(address)
            stateFlow.update {
                it.copy(
                    link = LinkStatus.Connected,
                    address = address,
                    name = snapshot.name ?: it.name,
                    fw = snapshot.fw,
                    snapshot = snapshot,
                    reconnectLeftSec = null,
                    notice = null,
                )
            }
            touch()
            ConnectOutcome.Connected(snapshot)
        } catch (failure: BridgeChannelException) {
            dropLinkLocked(
                LinkStatus.Disconnected,
                notice = "장치를 읽는 중 문제가 생겼습니다. 다시 찾아 연결하세요.",
            )
            ConnectOutcome.Failed(
                when (failure) {
                    is BridgeChannelException.Disconnected -> ConnectFailure.Other("읽는 중 연결이 끊겼습니다")
                    is BridgeChannelException.Timeout -> ConnectFailure.Other("장치가 응답하지 않습니다")
                    is BridgeChannelException.NotConnected -> ConnectFailure.Other("장치에 연결돼 있지 않습니다")
                },
            )
        }
    }

    /** 끊기: 감시 · 원문 수집 중단, 채널 정리, 상태 갱신. lock 안에서만 부른다. */
    private suspend fun dropLinkLocked(link: LinkStatus, notice: String? = null) {
        watchJob?.cancel()
        watchJob = null
        rawJob?.cancel()
        rawJob = null
        val channel = active
        active = null
        logged = null
        if (channel != null) {
            try {
                channel.disconnect()
            } catch (ignored: Exception) {
                // 이미 끊긴 채널이다. 상태만 정리한다.
            }
        }
        stateFlow.update {
            SessionState(link = link, scanning = it.scanning, address = it.address, notice = notice)
        }
    }

    private fun watch(channel: IBridgeChannel) {
        watchJob?.cancel()
        watchJob = scope.launch {
            channel.state.collect { st ->
                if (channel !== active || userClosed) return@collect
                when (st) {
                    ChannelState.Rebooting -> {
                        // 적용 중에는 스냅샷을 지우지 않는다. 워크플로가 끝나면 새 값으로 바꾼다.
                        stateFlow.update {
                            it.copy(link = LinkStatus.Rebooting, snapshot = if (workflowRunning) it.snapshot else null)
                        }
                        startReconnect()
                    }
                    ChannelState.Disconnected -> {
                        stateFlow.update {
                            it.copy(link = LinkStatus.Reconnecting, snapshot = if (workflowRunning) it.snapshot else null)
                        }
                        startReconnect()
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * 30초 동안 주소로 다시 연결하고 읽는다 (F-C5). 실패하면 끊김 상태로 둔다.
     * 적용 중이면 아무것도 하지 않는다: 워크플로의 WaitReady · VerifyWifi 가 재연결과 확인을 맡는다.
     */
    private fun startReconnect() {
        if (workflowRunning) return
        val t = target ?: return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            journal("자동 재연결 시작")
            val deadline = clock() + RECONNECT_WINDOW_MS
            val ticker = launch {
                while (isActive) {
                    val left = ((deadline - clock()) / 1000).toInt().coerceAtLeast(0)
                    stateFlow.update { it.copy(reconnectLeftSec = left) }
                    delay(1_000L)
                }
            }
            stateFlow.update {
                it.copy(link = LinkStatus.Reconnecting, reconnectLeftSec = RECONNECT_WINDOW_MS.div(1000).toInt(), notice = null)
            }
            var recovered = false
            while (!userClosed && !workflowRunning && clock() < deadline && !recovered) {
                recovered = lock.withLock {
                    if (userClosed || workflowRunning) return@withLock false
                    dropLinkLocked(LinkStatus.Reconnecting)
                    when (val link = linkLocked(t, RECONNECT_ATTEMPT_TIMEOUT, {})) {
                        is LinkResult.Linked -> {
                            readLocked(link.channel, t.address, {})
                            true
                        }
                        is LinkResult.Failed -> false
                    }
                }
            }
            ticker.cancel()
            journal(if (recovered) "자동 재연결 성공" else "자동 재연결 실패")
            if (!recovered && !userClosed && !workflowRunning) {
                lock.withLock {
                    dropLinkLocked(
                        LinkStatus.Disconnected,
                        notice = "다시 연결하지 못했습니다. 보드 탭에서 다시 찾으세요.",
                    )
                }
            }
        }
    }

    private suspend fun checkIdle() {
        if (stateFlow.value.link != LinkStatus.Connected || userClosed || workflowRunning) return
        if (clock() - lastActivityMs >= IDLE_LIMIT_MS) {
            journal("5분 유휴로 끊음")
            disconnect(notice = "5분 동안 사용하지 않아 연결을 끊었습니다. 다시 연결하려면 보드 탭에서 찾으세요.")
        }
    }

    private suspend fun handleChannelFailure(failure: BridgeChannelException) {
        if (failure is BridgeChannelException.Disconnected) {
            // 끊김 감시가 재연결을 시작한다. 여기서는 상태만 맞춘다.
            stateFlow.update { it.copy(snapshot = if (workflowRunning) it.snapshot else null) }
        }
    }

    /** 실패를 세어 비밀번호 틀림의 남은 횟수를 붙인다. 5회째는 연결 끊김으로 본다. */
    private fun countFailure(address: String, failure: ConnectFailure): ConnectFailure {
        if (failure !is ConnectFailure.WrongPassword) return failure
        val count = (wrongPasswordCounts[address] ?: 0) + 1
        if (count >= MAX_LOGIN_FAILURES) {
            wrongPasswordCounts.remove(address)
            return ConnectFailure.TooManyFailures
        }
        wrongPasswordCounts[address] = count
        return ConnectFailure.WrongPassword(MAX_LOGIN_FAILURES - count)
    }

    private fun touch() {
        lastActivityMs = clock()
    }

    private fun publishConsole() {
        consoleFlow.value = log.snapshot()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000L
        val CONNECT_TIMEOUT: Duration = CONNECT_TIMEOUT_MS.milliseconds
        const val RECONNECT_WINDOW_MS = 30_000L
        val RECONNECT_ATTEMPT_TIMEOUT: Duration = 8.seconds
        const val IDLE_LIMIT_MS = 5 * 60_000L
        const val IDLE_CHECK_MS = 15_000L
        const val MAX_LOGIN_FAILURES = 5
    }
}
