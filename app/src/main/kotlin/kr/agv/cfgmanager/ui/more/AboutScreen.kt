package kr.agv.cfgmanager.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.BuildConfig
import kr.agv.cfgmanager.share.ShareFiles
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import java.time.LocalDate

/** 지원하는 브리지 버전 범위 (docs/DEVELOPMENT_PLAN.md 3.6). 기능 유무는 출력으로 감지한다. */
const val SUPPORTED_BRIDGE_RANGE = "0.5.0 ~ 0.5.5"

/** 정보 화면 (F-G5): 버전 · 채널 · 커밋 · 지원 브리지 · 업데이트(준비 중) · 로그 내보내기 · 오픈소스 고지. */
@Composable
fun AboutScreen(container: AppContainer, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AgvSpacing.L),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
    ) {
        SectionTitle("앱")
        InfoLine("버전", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        InfoLine("채널", BuildConfig.CHANNEL)
        InfoLine("커밋", BuildConfig.SOURCE_REVISION)
        InfoLine("지원 브리지", SUPPORTED_BRIDGE_RANGE)

        SectionTitle("업데이트")
        Text(
            "업데이트 확인은 준비 중입니다. 지금은 새 버전을 직접 받아 설치하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
        ) { Text("업데이트 확인 (준비 중)") }

        SectionTitle("로그")
        Text(
            "최근 7일의 앱 로그를 공유합니다. 비밀번호는 기록하지 않습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryActionButton(
            text = "로그 내보내기",
            onClick = {
                scope.launch {
                    val today = LocalDate.now()
                    val text = withContext(Dispatchers.IO) { container.log.exportText(today) }
                    if (text.isEmpty()) {
                        onMessage("보관된 로그가 없습니다.")
                    } else {
                        val uri = runCatching {
                            withContext(Dispatchers.IO) { ShareFiles.writeText(context, "agv-log-$today.txt", text) }
                        }.getOrNull()
                        if (uri == null) {
                            onMessage("로그 파일을 만들지 못했습니다. 저장 공간을 확인하세요.")
                        } else {
                            context.startActivity(ShareFiles.chooser(uri, "text/plain", "앱 로그 공유"))
                        }
                    }
                }
            },
        )

        SectionTitle("오픈소스")
        LICENSES.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(AgvSpacing.Xl))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(AgvSizes.LabelColumn))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

private val LICENSES = listOf(
    "ZXing (zxing-android-embedded, zxing core) · Apache License 2.0",
    "Jetpack Compose · Material 3 · AndroidX · Navigation · Lifecycle · Activity · Apache License 2.0",
    "kotlinx.coroutines · kotlinx.serialization · JetBrains · Apache License 2.0",
    "Kotlin 표준 라이브러리 · Apache License 2.0",
)
