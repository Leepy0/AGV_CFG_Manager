package kr.agv.cfgmanager.ui.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.batch.BatchUi
import kr.agv.cfgmanager.ble.Preflight
import kr.agv.cfgmanager.core.bridge.CfgState
import kr.agv.cfgmanager.core.history.AgvHints
import kr.agv.cfgmanager.session.BoardRow
import kr.agv.cfgmanager.session.ConnectOutcome
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** 보드 탭의 상태와 동작. 회전해도 스캔 · 연결 시트 상태가 남는다. */
class BoardsViewModel(private val container: AppContainer) : ViewModel() {

    private val session = container.session
    private val batch = container.batch
    private val profiles = container.profiles
    private val history = container.history
    private val _ui = MutableStateFlow(BoardsUi())
    val ui: StateFlow<BoardsUi> = _ui.asStateFlow()

    private val connectedChannel = Channel<Unit>(Channel.BUFFERED)

    /** 연결이 끝나 설정 탭으로 가야 할 때 한 번 나온다. */
    val connected: Flow<Unit> = connectedChannel.receiveAsFlow()

    private var scanJob: Job? = null
    private var connectJob: Job? = null
    private var lastStartMs = 0L

    init {
        reloadHistory()
        viewModelScope.launch {
            batch.ui.collect { b -> _ui.update { it.copy(doneAddresses = doneOf(b), batchBanner = bannerOf(b)) } }
        }
    }

    /**
     * 스캔 시작. 블루투스 쪽 권한 · 블루투스 · 위치를 검사한다. 막혀 있어도 USB 콘솔은 따로 찾는다 (docs/DECISIONS.md, Phase 7).
     * 막힌 이유는 오류로 보이고, USB 행이 있으면 목록을 함께 보인다.
     */
    fun startScan() {
        if (scanJob?.isActive == true) return
        val wirelessBlock = if (container.isFakeMode()) null else wirelessBlockOf(container.preflight.check())
        val now = now()
        if (lastStartMs != 0L && now - lastStartMs < RESCAN_GAP_MS) {
            _ui.update { it.copy(notice = "스캔은 6초 간격으로만 시작할 수 있습니다. 잠시 후 다시 누르세요.") }
            return
        }
        lastStartMs = now
        reloadHistory()
        _ui.update {
            it.copy(scanState = ScanState.Scanning, rows = emptyList(), nowMs = now, scanStartedMs = now, error = wirelessBlock, notice = null)
        }
        session.setScanning(true)
        scanJob = viewModelScope.launch {
            val ticker = launch {
                while (isActive) {
                    _ui.update { it.copy(nowMs = now()) }
                    delay(TICK_MS)
                }
            }
            try {
                val duration = container.settings.current.scanSeconds.seconds
                val rowsFlow: Flow<List<BoardRow>> = when {
                    container.isFakeMode() -> container.boards.discover(duration)
                    wirelessBlock != null -> container.usb.discover(duration)
                    else -> combine(container.usb.discover(duration), container.boards.discover(duration)) { wired, wireless ->
                        wired + wireless
                    }
                }
                rowsFlow.collect { rows: List<BoardRow> ->
                    _ui.update { it.copy(rows = rows, nowMs = now()) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _ui.update { it.copy(error = ScanError.Failed) }
            } finally {
                ticker.cancel()
                session.setScanning(false)
                _ui.update { it.copy(scanState = ScanState.Done, nowMs = now()) }
            }
        }
    }

    /** 자동 중지 전에 사용자가 멈춘다. */
    fun stopScan() {
        scanJob?.cancel()
    }

    fun openSheet(row: BoardRow) {
        _ui.update { it.copy(sheet = ConnectSheet(row = row, password = activeBlePassword())) }
    }

    fun closeSheet() {
        if (connectJob?.isActive == true) return
        _ui.update { it.copy(sheet = null) }
    }

    fun setPassword(value: String) {
        _ui.update { st -> st.copy(sheet = st.sheet?.copy(password = value, failure = null)) }
    }

    /** 연결 · 로그인 · 읽기. 성공하면 시트를 닫고 [connected] 를 한 번 보낸다. 연속 세팅 중이면 읽기 결과를 알린다. */
    fun connect() {
        val sheet = _ui.value.sheet ?: return
        // USB 콘솔은 로그인이 없으므로 비밀번호를 묻지 않는다.
        if (connectJob?.isActive == true || (!sheet.row.isUsb && sheet.password.isEmpty())) return
        connectJob = viewModelScope.launch {
            _ui.update { st -> st.copy(sheet = st.sheet?.copy(busy = true, stage = null, failure = null)) }
            batch.pick(sheet.row.address)
            val outcome = session.connect(sheet.row.address, sheet.row.name, sheet.password) { stage ->
                _ui.update { st -> st.copy(sheet = st.sheet?.copy(stage = stage)) }
            }
            when (outcome) {
                is ConnectOutcome.Connected -> {
                    // 모르면 설정됨으로 본다. 잘못 채워 덮어쓰는 일을 막으려고다.
                    batch.readFinished(outcome.snapshot.cfgState ?: CfgState.SAVED)
                    _ui.update { it.copy(sheet = null) }
                    connectedChannel.trySend(Unit)
                }
                is ConnectOutcome.Failed -> {
                    batch.readFailed()
                    _ui.update { st ->
                        st.copy(sheet = st.sheet?.copy(busy = false, stage = null, failure = outcome.failure))
                    }
                }
            }
        }
    }

    fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        batch.readFailed()
        _ui.update { st -> st.copy(sheet = st.sheet?.copy(busy = false, stage = null)) }
    }

    private fun activeBlePassword(): String {
        val doc = profiles.document.value ?: return ""
        return doc.profiles.firstOrNull { it.id == doc.lastProfileId }?.blePassword.orEmpty()
    }

    private fun reloadHistory() {
        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { history.readAll() }
                _ui.update { it.copy(historyAgv = AgvHints.latestAgvByAddress(entries)) }
            } catch (failure: IOException) {
                // 이력을 못 읽으면 표식 없이 보인다
            }
        }
    }

    private fun doneOf(b: BatchUi): Set<String> = b.state.done.map { it.bleAddress.uppercase() }.toSet()

    private fun bannerOf(b: BatchUi): String? =
        if (!b.enabled) null else "다음: ${b.state.nextAgv}호기 · ${b.profileName ?: "프로필 없음"}"

    /** 블루투스 쪽 검사 결과를 스캔 오류로 바꾼다. 막히지 않았으면 null. */
    private fun wirelessBlockOf(check: Preflight): ScanError? = when (check) {
        Preflight.Ready -> null
        is Preflight.NeedPermission -> ScanError.Permission(check.permissions)
        Preflight.BluetoothOff -> ScanError.BluetoothOff
        Preflight.LocationOff -> ScanError.LocationOff
    }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val TICK_MS = 1_000L
        const val RESCAN_GAP_MS = 6_000L
    }
}
