package kr.agv.cfgmanager.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.core.history.HistoryEntry
import kr.agv.cfgmanager.core.history.HistoryView
import kr.agv.cfgmanager.share.ShareFiles
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SmallTextAction
import kr.agv.cfgmanager.ui.components.StatePanel
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate

/**
 * 이력 탭 (5.5, F-H2 · H3). 목록 · 검색 · 기간 필터 · 상세 시트 · CSV 공유.
 * [onFilled] 는 폼 채우기 뒤 설정 탭으로 간다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(container: AppContainer, onFilled: () -> Unit) {
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory {
        initializer { HistoryViewModel(container) }
    })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val visible = ui.visible

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = AgvSpacing.L)) {
        Spacer(modifier = Modifier.size(AgvSpacing.S))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "이력 ${visible.size}건",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            SmallTextAction(
                text = "CSV 공유",
                enabled = visible.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val name = "agv-history-${LocalDate.now()}.csv"
                        val csv = vm.csvOfVisible()
                        val uri = withContext(Dispatchers.IO) { ShareFiles.writeText(context, name, csv) }
                        context.startActivity(Intent.createChooser(ShareFiles.sendIntent(uri, "text/csv"), "이력 공유"))
                    }
                },
            )
        }
        OutlinedTextField(
            value = ui.query,
            onValueChange = vm::setQuery,
            singleLine = true,
            label = { Text("검색 (프로필 · 호기 · 장치 · IP · SSID)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AgvSpacing.S), modifier = Modifier.padding(vertical = AgvSpacing.Xxs)) {
            HistoryPeriod.entries.forEach { period ->
                FilterChip(
                    selected = ui.period == period,
                    onClick = { vm.setPeriod(period) },
                    label = { Text(period.label) },
                )
            }
        }

        when {
            !ui.loaded -> StatePanel(title = "불러오는 중", body = "이력을 읽고 있습니다.")
            ui.failed -> StatePanel(
                title = "이력을 읽지 못했습니다",
                body = "저장 공간을 확인한 뒤 다시 시도하세요.",
                isError = true,
                primary = kr.agv.cfgmanager.ui.components.ActionSpec("다시 시도") { vm.reload() },
            )
            ui.all.isEmpty() -> StatePanel(
                title = "아직 기록이 없습니다",
                body = "적용을 한 번 마치면 여기에 성공과 실패가 모두 남습니다.",
            )
            visible.isEmpty() -> StatePanel(
                title = "조건에 맞는 기록이 없습니다",
                body = "검색어나 기간을 바꿔 보세요.",
                primary = kr.agv.cfgmanager.ui.components.ActionSpec("검색 지우기") {
                    vm.setQuery("")
                    vm.setPeriod(HistoryPeriod.All)
                },
            )
            else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(visible) { entry ->
                    HistoryRow(entry) { vm.select(entry) }
                    HorizontalDivider()
                }
            }
        }
    }

    ui.selected?.let { entry ->
        ModalBottomSheet(onDismissRequest = { vm.select(null) }) {
            HistoryDetail(
                entry = entry,
                onFill = {
                    vm.fillForm(entry)
                    vm.select(null)
                    onFilled()
                },
                onCopyLabel = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("AGV 라벨", labelText(entry)))
                },
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    val success = HistoryView.isSuccess(entry.result)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AgvSizes.ListRowMin)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AgvSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${formatAt(entry.at)} · ${entry.profileName ?: "프로필 없음"} · ${agvText(entry.agv)}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(entry.deviceName, entry.ip).joinToString(" · ").ifEmpty { "장치 정보 없음" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            if (success) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (success) MaterialTheme.colorScheme.onSurface else AgvTheme.status.alarm,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.size(AgvSpacing.Xxs))
        Text(
            HistoryView.resultLabel(entry.result),
            style = MaterialTheme.typography.labelMedium,
            color = if (success) MaterialTheme.colorScheme.onSurfaceVariant else AgvTheme.status.alarm,
        )
    }
}

@Composable
private fun HistoryDetail(entry: HistoryEntry, onFill: () -> Unit, onCopyLabel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AgvSpacing.L).padding(bottom = AgvSpacing.L),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
    ) {
        Text("${agvText(entry.agv)} · ${HistoryView.resultLabel(entry.result)}", style = MaterialTheme.typography.titleMedium)
        DetailRow("시각", formatAt(entry.at))
        DetailRow("프로필", entry.profileName ?: "—")
        DetailRow("장치 이름", entry.deviceName ?: "—")
        DetailRow("IP", entry.ip ?: "—")
        DetailRow("SSID", entry.ssid ?: "—")
        DetailRow("MAC", entry.mac ?: "—")
        DetailRow("BLE 주소", entry.bleAddress ?: "—")
        DetailRow("펌웨어", entry.fw ?: "—")
        DetailRow("연결", if (entry.channel == "fake") "가짜 브리지" else "BLE")
        DetailRow("작업", if (entry.action == "apply") "적용" else entry.action)
        DetailRow("RSSI", entry.rssi?.let { "$it dBm" } ?: "—")
        if (entry.note.isNotBlank()) DetailRow("메모", entry.note)
        PrimaryActionButton(text = "이 값으로 폼 채우기", onClick = onFill)
        OutlinedButton(onClick = onCopyLabel, modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin)) {
            Text("라벨 텍스트 복사")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(AgvSizes.LabelColumn))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal)
    }
}

private fun agvText(agv: Int?): String = agv?.let { "%02d호기".format(it) } ?: "호기 —"

private fun labelText(entry: HistoryEntry): String =
    listOfNotNull(
        entry.agv?.let { "AGV-%02d".format(it) },
        entry.deviceName,
        entry.ip,
        entry.mac,
    ).joinToString(" · ")

private val listTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/** UTC 기록 시각을 휴대폰 시간대로. 형식이 틀리면 원문을 그대로 쓴다. */
private fun formatAt(at: String): String = runCatching {
    listTimeFormat.withZone(ZoneId.systemDefault()).format(Instant.parse(at))
}.getOrDefault(at)
