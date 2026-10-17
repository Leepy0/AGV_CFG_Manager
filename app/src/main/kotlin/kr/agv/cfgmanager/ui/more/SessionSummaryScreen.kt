package kr.agv.cfgmanager.ui.more

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import kr.agv.cfgmanager.core.workflows.BatchDone
import kr.agv.cfgmanager.core.workflows.SessionSummary
import kr.agv.cfgmanager.share.ShareFiles
import kr.agv.cfgmanager.ui.components.PrimaryActionButton
import kr.agv.cfgmanager.ui.components.StatePanel
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import java.time.LocalDate

/**
 * 연속 세팅 요약 (F-B3). 연속 모드를 새로 켜면 목록이 비워진다.
 * 공유하면 평문 표 (호기 · 장치 이름 · 결과) 가 나간다. 비밀번호와 주소는 없다.
 */
@Composable
fun SessionSummaryScreen(container: AppContainer, onMessage: (String) -> Unit) {
    val batch by container.batch.ui.collectAsStateWithLifecycle()
    val done: List<BatchDone> = batch.state.done
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (done.isEmpty()) {
        StatePanel(
            title = "아직 설정한 호기가 없습니다",
            body = "세팅 탭에서 연속 모드를 켜고 보드를 하나씩 설정하면 여기에 쌓입니다.",
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = AgvSpacing.L),
        verticalArrangement = Arrangement.spacedBy(AgvSpacing.S),
    ) {
        Text("설정한 호기 ${done.size}대", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin), verticalAlignment = Alignment.CenterVertically) {
            Text("호기", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.25f))
            Text("장치 이름", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.5f))
            Text("결과", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.25f))
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(done) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = AgvSizes.TouchMin).padding(vertical = AgvSpacing.Xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%02d".format(entry.agv), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.25f))
                    Text(entry.deviceName ?: "-", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.5f))
                    Text(
                        if (entry.result == "ok") "성공" else "확인 필요",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(0.25f),
                    )
                }
                HorizontalDivider()
            }
        }
        PrimaryActionButton(
            text = "요약 공유",
            onClick = {
                val text = SessionSummary.toText(done, LocalDate.now().toString())
                scope.launch { shareSummary(context, text, onMessage) }
            },
        )
    }
}

private suspend fun shareSummary(context: Context, text: String, onMessage: (String) -> Unit) {
    val name = "agv-session-${LocalDate.now()}.txt"
    val uri = runCatching {
        withContext(Dispatchers.IO) { ShareFiles.writeText(context, name, text) }
    }.getOrNull()
    if (uri == null) {
        onMessage("요약 파일을 만들지 못했습니다. 저장 공간을 확인하세요.")
    } else {
        context.startActivity(ShareFiles.chooser(uri, "text/plain", "연속 세팅 요약 공유"))
    }
}
