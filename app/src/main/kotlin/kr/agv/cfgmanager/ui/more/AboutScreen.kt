package kr.agv.cfgmanager.ui.more

import android.content.Context
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import kr.agv.cfgmanager.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.BuildConfig
import kr.agv.cfgmanager.core.update.LatestRelease
import kr.agv.cfgmanager.core.update.UpdateSpec
import kr.agv.cfgmanager.core.update.UpdateState
import kr.agv.cfgmanager.share.ShareFiles
import kr.agv.cfgmanager.update.UpdateActions
import kr.agv.cfgmanager.update.UpdateInstaller
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.SectionTitle
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import java.time.LocalDate

/** 지원하는 브리지 버전 범위 (docs/DEVELOPMENT_PLAN.md 3.6). 기능 유무는 출력으로 감지한다. */
const val SUPPORTED_BRIDGE_RANGE = "0.5.0 ~ 0.5.5"

/** 정보 화면 (F-G5): 버전 · 채널 · 커밋 · 지원 브리지 · 업데이트 · 로그 내보내기 · 오픈소스 고지. */
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

        UpdateSection(container, onMessage)

        SectionTitle("로그")
        Text(
            "최근 7일의 앱 로그를 공유합니다. 비밀번호는 기록하지 않습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 이 화면의 주요 행동은 업데이트 확인 하나다. 로그 내보내기는 보조 버튼으로 낮춘다.
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
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
        ) { Text("로그 내보내기") }

        SectionTitle("오픈소스")
        LICENSES.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.size(AgvSpacing.Xl))
    }
}

/** 업데이트 (F-G3, 7.5). 상태에 따라 한 가지 주요 행동을 보인다. 설치는 `REQUEST_INSTALL_PACKAGES` 허용 여부를 먼저 본다. */
@Composable
private fun UpdateSection(container: AppContainer, onMessage: (String) -> Unit) {
    val state by container.updates.state.collectAsStateWithLifecycle()
    val actions = container.updateActions
    val context = LocalContext.current

    SectionTitle("업데이트")
    when (val current = state) {
        UpdateState.Idle -> UpdateText("아직 확인하지 않았습니다.")
        UpdateState.Checking -> {
            UpdateText("최신 버전을 확인하는 중입니다.")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is UpdateState.UpToDate -> UpdateText("최신 버전입니다 (${current.version}).")
        is UpdateState.Available -> AvailableText(current.release, current.installableHere, current.overwriteNote)
        is UpdateState.Downloading -> {
            UpdateText(current.percent?.let { "받는 중 $it%" } ?: "받는 중입니다.")
            val fraction = current.percent?.let { it / 100f }
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        is UpdateState.Ready -> UpdateText("받았고 sha256 을 확인했습니다. [설치]를 누르세요.")
        is UpdateState.Failed -> UpdateText("오류: ${current.message}", error = true)
    }

    when (val current = state) {
        is UpdateState.Downloading -> OutlinedButton(
            onClick = actions::cancelDownload,
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
        ) { Text("받기 취소") }

        UpdateState.Checking -> Unit

        is UpdateState.Ready -> {
            PrimaryActionButton(
                text = "설치",
                onClick = {
                    if (!UpdateInstaller.canInstallPackages(context)) {
                        runCatching { context.startActivity(UpdateInstaller.unknownSourcesSettings(context)) }
                        onMessage("'이 앱 설치 허용'을 켠 뒤 다시 [설치]를 누르세요.")
                    } else {
                        runCatching { context.startActivity(UpdateInstaller.installIntent(context, current.apk)) }
                            .onFailure { onMessage("설치 화면을 열지 못했습니다. 다시 받아 보세요.") }
                    }
                },
            )
            CheckButton(actions)
        }

        is UpdateState.Available -> {
            if (current.installableHere && current.release.installableInApp) {
                PrimaryActionButton(text = "받기", onClick = actions::download)
                BrowserButton(context, primary = false)
            } else if (current.installableHere) {
                // sha256 이 없는 폴백 결과: 앱에서 받지 않고 브라우저로 받게 한다.
                BrowserButton(context, primary = true)
            }
            CheckButton(actions)
        }

        is UpdateState.Failed -> {
            PrimaryActionButton(text = "다시 확인", onClick = { actions.check(silent = false) })
            BrowserButton(context, primary = false)
        }

        UpdateState.Idle, is UpdateState.UpToDate -> PrimaryActionButton(
            text = "지금 확인",
            onClick = { actions.check(silent = false) },
        )
    }
}

@Composable
private fun AvailableText(release: LatestRelease, installableHere: Boolean, overwriteNote: Boolean) {
    UpdateText("업데이트 있음: ${release.version}", emphasized = true)
    release.notes?.let { UpdateText(it) }
    if (overwriteNote) {
        UpdateText("dev 빌드는 정식으로 덮어 설치됩니다. 같은 서명 키의 정식 APK 만 덮어 설치됩니다.")
    }
    if (!installableHere) {
        val minimum = release.minSdk?.let { "Android $it 이상" } ?: "더 높은 Android"
        UpdateText("이 휴대폰은 $minimum 이 필요해 설치할 수 없습니다.", error = true)
    } else if (!release.installableInApp) {
        UpdateText("이 버전은 앱에서 받을 수 없습니다. 브라우저로 받아 설치하세요.")
    }
}

@Composable
private fun UpdateText(text: String, emphasized: Boolean = false, error: Boolean = false) {
    Text(
        text,
        style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = when {
            error -> MaterialTheme.colorScheme.error
            emphasized -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun CheckButton(actions: UpdateActions) {
    OutlinedButton(
        onClick = { actions.check(silent = false) },
        modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
    ) { Text("지금 확인") }
}

@Composable
private fun BrowserButton(context: Context, primary: Boolean) {
    val onClick: () -> Unit = {
        runCatching { context.startActivity(UpdateInstaller.browserIntent(UpdateSpec.BROWSER_APK_URL)) }
    }
    if (primary) {
        PrimaryActionButton(text = "브라우저로 받기", onClick = onClick)
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin),
        ) { Text("브라우저로 받기") }
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
