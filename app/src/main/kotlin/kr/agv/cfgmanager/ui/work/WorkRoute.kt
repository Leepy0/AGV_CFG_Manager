package kr.agv.cfgmanager.ui.work

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.AppContainer
import kr.agv.cfgmanager.apply.ApplyUi
import kr.agv.cfgmanager.session.LinkStatus
import kr.agv.cfgmanager.ui.boards.BoardsRoute
import kr.agv.cfgmanager.ui.config.SettingsRoute

/**
 * 세팅 탭: 현장 작업 순서를 한 화면에서 따라간다.
 *
 *  1. 보드 고르기 — 현장 프로필을 고르고 목록에서 보드를 누른다 (자동으로 찾는다, 비밀번호를 알면 바로 연결).
 *  2. 확인 · 적용 — 연결되면 같은 자리가 호기 번호 + 적용할 값 요약 + [적용] 으로 바뀐다.
 *  3. 결과 — 성공하면 [다음 호기] 가 기본 버튼이고, 누르면 1 로 돌아가 다시 찾는다.
 *
 * 연결 중(시트가 진행을 보인다)에는 1 에 머물고, 연결 · 재부팅 · 재연결 중이거나 적용이 끝나지 않았으면 2 를 보인다.
 */
@Composable
fun WorkRoute(
    container: AppContainer,
    onOpenConsole: () -> Unit,
    onEditProfile: (String?) -> Unit,
) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val apply by container.apply.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val onLink = session.link == LinkStatus.Connected ||
        session.link == LinkStatus.Rebooting ||
        session.link == LinkStatus.Reconnecting
    if (onLink || apply !is ApplyUi.Idle) {
        SettingsRoute(
            container = container,
            onOpenConsole = onOpenConsole,
            onOpenBoards = {
                // 다음 보드로: 읽은 보드를 버리고 목록 상태로 돌아간다 (7.3).
                scope.launch {
                    container.batch.backToList()
                    container.session.disconnect()
                }
            },
            onEditProfile = onEditProfile,
        )
    } else {
        BoardsRoute(container = container)
    }
}
