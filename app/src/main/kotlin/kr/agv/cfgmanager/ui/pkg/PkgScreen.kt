package kr.agv.cfgmanager.ui.pkg

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.core.pkg.PkgSlot
import kr.agv.cfgmanager.core.pkg.PkgState
import kr.agv.cfgmanager.pkg.PkgSaver
import kr.agv.cfgmanager.pkg.PkgUi
import kr.agv.cfgmanager.ui.components.ActionSpec
import kr.agv.cfgmanager.ui.components.Button
import kr.agv.cfgmanager.ui.components.ConfirmDialog
import kr.agv.cfgmanager.ui.components.InsetDivider
import kr.agv.cfgmanager.ui.components.InsetGroup
import kr.agv.cfgmanager.ui.components.OutlinedButton
import kr.agv.cfgmanager.ui.components.StatePanel
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme
import java.util.Locale

private const val SAVE_FAILED = "저장 실패"

/** 메인보드 원본 (.out · .map) 받기 · 저장. 받기는 앱 수명 컨트롤러가 돌리므로 화면을 나가도 이어진다. */
@Composable
fun PkgScreen(container: AppContainer) {
    val controller = container.pkg
    val ui by controller.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PkgSlot?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }

    // 받는 중이거나 저장을 기다리는 상태는 지키고, 그 밖에는 들어올 때 목록을 읽는다
    LaunchedEffect(Unit) {
        val now = controller.ui.value
        if (now !is PkgUi.Downloading && now !is PkgUi.ReadyToSave) controller.refresh()
    }

    // 받는 동안 화면을 켜 둔다. 끝나면 들어오기 전 값으로 되돌린다
    val view = LocalView.current
    val downloading = ui is PkgUi.Downloading
    DisposableEffect(downloading) {
        val before = view.keepScreenOn
        if (downloading) view.keepScreenOn = true
        onDispose { view.keepScreenOn = before }
    }

    // 폴더 권한은 이번 저장에만 쓰고 영구 보관하지 않는다
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        val ready = controller.ui.value as? PkgUi.ReadyToSave
        if (tree == null || ready == null) return@rememberLauncherForActivityResult
        saving = true
        saveError = false
        container.appScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { PkgSaver.save(context.contentResolver, tree, ready.folder, ready.files) }.isSuccess
            }
            saving = false
            if (saved) {
                controller.onSaved()
            } else {
                saveError = true
                controller.onSaveFailed(SAVE_FAILED)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgvSpacing.L),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
    ) {
        when (val state = ui) {
            PkgUi.NotConnected -> StatePanel("연결 안 됨", primary = ActionSpec("다시 읽기", controller::refresh))
            PkgUi.Loading -> Column(
                modifier = Modifier.fillMaxWidth().padding(AgvSpacing.Xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
            ) {
                CircularProgressIndicator()
                Text("읽는 중", style = MaterialTheme.typography.bodyMedium)
            }
            PkgUi.Unsupported -> StatePanel(
                title = "읽기 실패 또는 브리지 0.9.1 미만",
                primary = ActionSpec("다시 읽기", controller::refresh),
            )
            PkgUi.NoPartition -> StatePanel("패키지 파티션 없음", primary = ActionSpec("다시 읽기", controller::refresh))
            PkgUi.Empty -> StatePanel("패키지 없음", primary = ActionSpec("다시 읽기", controller::refresh))
            is PkgUi.Slots -> SlotList(state, onDownload = { pending = it })
            is PkgUi.Downloading -> DownloadPanel(state, onCancel = controller::cancel)
            is PkgUi.ReadyToSave -> Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = AgvSpacing.L),
                verticalArrangement = Arrangement.spacedBy(AgvSpacing.M),
            ) {
                Text("받기 완료", style = MaterialTheme.typography.titleMedium)
                Text(state.folder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.sourceCount > 0) {
                    Text(
                        "소스 ${state.sourceCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (saveError) {
                    Text(SAVE_FAILED, style = MaterialTheme.typography.bodyMedium, color = AgvTheme.status.alarm)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S)) {
                    Button(
                        onClick = {
                            try {
                                picker.launch(null)
                            } catch (_: ActivityNotFoundException) {
                                saveError = true
                                controller.onSaveFailed(SAVE_FAILED)
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.weight(1f).heightIn(min = AgvSizes.PrimaryAction),
                    ) { Text(if (saving) "저장 중" else "저장") }
                    OutlinedButton(
                        onClick = controller::refresh,
                        enabled = !saving,
                        modifier = Modifier.weight(1f).heightIn(min = AgvSizes.PrimaryAction),
                    ) { Text("목록") }
                }
            }
            is PkgUi.Saved -> StatePanel("저장됨", body = state.folder, primary = ActionSpec("목록", controller::refresh))
            is PkgUi.Error -> StatePanel(
                title = state.text,
                isError = true,
                primary = if (state.canRetry) ActionSpec("다시 시도", controller::retry) else ActionSpec("목록", controller::refresh),
                secondary = if (state.canRetry) ActionSpec("목록", controller::refresh) else null,
            )
        }
    }

    pending?.let { slot ->
        ConfirmDialog(
            title = "AGV 정지",
            text = "받는 동안 무선 통신 지연",
            confirmLabel = "받기",
            cancelFocused = true,
            onConfirm = {
                pending = null
                controller.download(slot)
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun SlotList(state: PkgUi.Slots, onDownload: (PkgSlot) -> Unit) {
    // 추천 슬롯을 맨 위에 둔다
    val ordered = state.slots.sortedBy { if (it.index == state.preferred) 0 else 1 }
    InsetGroup {
        ordered.forEachIndexed { position, slot ->
            if (position > 0) InsetDivider()
            SlotRow(slot, preferred = slot.index == state.preferred, onDownload = { onDownload(slot) })
        }
    }
}

/** 한 줄: 상태 · 버전 · 크기 · 받기. */
@Composable
private fun SlotRow(slot: PkgSlot, preferred: Boolean, onDownload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .padding(horizontal = AgvSpacing.L),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgvSpacing.M),
    ) {
        Text(stateLabel(slot.state), style = MaterialTheme.typography.titleMedium)
        Text(slot.ver, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(formatSize(slot.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val buttonModifier = Modifier.heightIn(min = AgvSizes.TouchMin)
        if (preferred) {
            Button(onClick = onDownload, modifier = buttonModifier) { Text("받기") }
        } else {
            OutlinedButton(onClick = onDownload, modifier = buttonModifier) { Text("받기") }
        }
    }
}

@Composable
private fun DownloadPanel(state: PkgUi.Downloading, onCancel: () -> Unit) {
    val percent = if (state.total > 0) (state.done * 100 / state.total).toInt() else 0
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AgvSpacing.L),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.M),
    ) {
        Text("받는 중 ${state.slot.ver}", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "$percent%  ${formatSize(state.done)} / ${formatSize(state.total)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.PrimaryAction)) {
            Text("취소")
        }
    }
}

private fun stateLabel(state: PkgState): String = when (state) {
    PkgState.Applied -> "적용"
    PkgState.Staged -> "대기"
    PkgState.Old -> "이전"
}

/** 1 MB 미만은 KB, 그 이상은 MB (소수 한 자리). */
internal fun formatSize(bytes: Long): String =
    if (bytes < MB) "${(bytes + KB - 1) / KB} KB" else "%.1f MB".format(Locale.ROOT, bytes.toDouble() / MB)

private const val KB = 1024L
private const val MB = 1024L * 1024L
