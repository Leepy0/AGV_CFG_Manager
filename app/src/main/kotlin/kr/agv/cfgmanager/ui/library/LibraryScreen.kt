package kr.agv.cfgmanager.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kr.agv.cfgmanager.ui.components.InsetDivider
import kr.agv.cfgmanager.ui.components.insetGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.data.WifiEntry
import kr.agv.cfgmanager.ui.components.ConfirmDialog
import kr.agv.cfgmanager.ui.components.StatePanel
import kr.agv.cfgmanager.ui.theme.AgvMonoText
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing

/** SSID 라이브러리 화면 상태: 보기 중인 비밀번호 (메모리에만 둔다). */
class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val networks = container.networks
    val entries: StateFlow<List<WifiEntry>> = networks.entries

    private val revealedFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val revealed: StateFlow<Map<String, String>> = revealedFlow.asStateFlow()

    /** 비밀번호 보기 토글. 보일 때만 복호화한다. */
    fun toggleReveal(ssid: String) {
        if (revealedFlow.value.containsKey(ssid)) {
            revealedFlow.update { it - ssid }
            return
        }
        viewModelScope.launch {
            val password = withContext(Dispatchers.IO) { networks.password(ssid) }
            if (password != null) {
                revealedFlow.update { it + (ssid to (if (password.isEmpty()) "(오픈 AP · 비밀번호 없음)" else password)) }
            }
        }
    }

    fun delete(ssid: String) {
        revealedFlow.update { it - ssid }
        viewModelScope.launch { networks.delete(ssid) }
    }
}

/** SSID 라이브러리 (F-W3): 목록 · 마지막 사용 · 사용 횟수 · 비밀번호 보기 · 삭제. */
@Composable
fun LibraryRoute(container: AppContainer) {
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory {
        initializer { LibraryViewModel(container) }
    })
    val entries by vm.entries.collectAsStateWithLifecycle()
    val revealed by vm.revealed.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }

    if (entries.isEmpty()) {
        StatePanel(
            title = "저장된 SSID 가 없습니다",
            body = "적용에 성공하면 SSID 와 비밀번호가 자동으로 저장됩니다. 저장을 끄려면 세팅 탭의 체크를 끄세요.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AgvSpacing.L).insetGroup(),
        contentPadding = PaddingValues(horizontal = AgvSpacing.L),
    ) {
        items(entries, key = { it.ssid }) { entry ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = AgvSpacing.S)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.ssid, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "마지막 사용 ${entry.lastUsedAt?.take(10) ?: "—"} · ${entry.useCount}회",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.toggleReveal(entry.ssid) }, modifier = Modifier.heightIn(min = AgvSizes.TouchMin)) {
                        Text(if (revealed.containsKey(entry.ssid)) "숨기기" else "비밀번호 보기")
                    }
                    // 삭제는 보기와 떨어뜨리고 위험색으로 구분한다.
                    TextButton(
                        onClick = { confirm = entry.ssid },
                        modifier = Modifier.padding(start = AgvSpacing.L).heightIn(min = AgvSizes.TouchMin),
                    ) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
                revealed[entry.ssid]?.let { text ->
                    Text(text, style = AgvMonoText, modifier = Modifier.padding(top = AgvSpacing.Xxs))
                }
                if (!entry.hasPassword && !entry.open) {
                    Text(
                        "저장된 비밀번호를 읽지 못했습니다(기기 키 변경). 적용할 때 다시 입력하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            InsetDivider()
        }

    }

    confirm?.let { ssid ->
        ConfirmDialog(
            title = "\"$ssid\" 를 지울까요?",
            text = "저장된 비밀번호도 함께 지워집니다. 되돌릴 수 없습니다.",
            confirmLabel = "삭제",
            danger = true,
            onConfirm = {
                vm.delete(ssid)
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}
