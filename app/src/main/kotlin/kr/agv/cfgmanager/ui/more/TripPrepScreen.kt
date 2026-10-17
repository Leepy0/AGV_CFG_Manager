package kr.agv.cfgmanager.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.share.ShareFiles
import kr.agv.cfgmanager.ui.components.ConfirmDialog
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.profiles.ProfilesViewModel
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme
import java.time.LocalDate

/** 출장 체크리스트 (앱 밖 준비 포함). 체크 상태는 화면에만 남는다. */
val TRIP_CHECKLIST: List<String> = listOf(
    "프로필과 SSID 라이브러리를 파일 또는 QR 로 옮겼다",
    "각 프로필의 BLE 비밀번호를 확인했다 (기본 agvsetup, 바꿨다면 그 값)",
    "현장 Wi-Fi 의 SSID 와 비밀번호를 알고 있다",
    "게이트웨이 PC 의 주소와 같은 망에 붙을 수 있다",
    "휴대폰의 블루투스 · 위치 권한을 허용했다",
    "메인보드 굽기가 필요할 수 있어 PC 와 USB 케이블을 챙겼다 (앱 범위 밖)",
    "브리지 펌웨어 파일은 PC 에 받아 두었다 (앱 범위 밖)",
)

/** 출장 준비 (F-G4): 프로필 · 라이브러리 내보내기 한 번에 + 체크리스트. */
@Composable
fun TripPrepScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: ProfilesViewModel = viewModel(factory = viewModelFactory {
        initializer { ProfilesViewModel(container) }
    })
    val checks = remember { mutableStateListOf<Boolean>().apply { repeat(TRIP_CHECKLIST.size) { add(false) } } }
    var includeLibrary by remember { mutableStateOf(true) }
    var includePasswords by remember { mutableStateOf(false) }
    var warnPasswords by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgvSpacing.L),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
    ) {
        SectionTitle("출장 전 체크리스트")
        TRIP_CHECKLIST.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AgvSizes.TouchMin)
                    .clickable(role = Role.Checkbox) { checks[index] = !checks[index] },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checks[index], onCheckedChange = null)
                Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = AgvSpacing.S))
            }
        }
        Text(
            "체크 ${checks.count { it }} / ${TRIP_CHECKLIST.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("한 번에 내보내기")
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin).clickable(role = Role.Checkbox) {
                includeLibrary = !includeLibrary
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = includeLibrary, onCheckedChange = null)
            Text("SSID 라이브러리도 포함", style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin).clickable(role = Role.Checkbox) {
                if (includePasswords) includePasswords = false else warnPasswords = true
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = includePasswords, onCheckedChange = null)
            Text("비밀번호 포함 (평문)", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (includePasswords) "파일에 비밀번호가 평문으로 들어갑니다. 공유 뒤에는 지우세요." else "비밀번호는 빠집니다. 현장에서 BLE 비밀번호를 다시 입력해야 할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = if (includePasswords) AgvTheme.status.alarm else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryActionButton(
            text = "내보내기 파일 공유",
            onClick = {
                vm.buildExport(includePasswords, includeLibrary) { text ->
                    scope.launch {
                        val uri = runCatching {
                            withContext(Dispatchers.IO) {
                                ShareFiles.writeText(context, "agvcfg-${LocalDate.now()}.agvcfg.json", text)
                            }
                        }.getOrNull()
                        if (uri == null) {
                            message = "파일을 만들지 못했습니다. 저장 공간을 확인하세요."
                        } else {
                            context.startActivity(ShareFiles.chooser(uri, "application/json", "출장 준비 파일 공유"))
                        }
                    }
                }
            },
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AgvTheme.status.alarm)
        }
        Text(
            "같은 내용을 QR 로 옮기려면 프로필 편집 화면의 [QR 보기] 를 쓰세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (warnPasswords) {
        ConfirmDialog(
            title = "비밀번호를 파일에 넣습니다",
            text = "Wi-Fi 비밀번호와 BLE 비밀번호가 평문으로 들어갑니다. 파일을 안전한 곳에만 보관하세요.",
            confirmLabel = "포함",
            danger = true,
            onConfirm = {
                includePasswords = true
                warnPasswords = false
            },
            onDismiss = { warnPasswords = false },
        )
    }
}
