package kr.agv.cfgmanager.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.agv.cfgmanager.core.update.UpdateController

/**
 * 화면이 부르는 업데이트 동작. 작업은 앱 수명 scope 에서 돌아서 화면을 떠나도 끝까지 간다 (받기 취소만 명시적으로 한다).
 */
class UpdateActions(
    private val controller: UpdateController,
    private val scope: CoroutineScope,
) {
    private var download: Job? = null

    fun check(silent: Boolean) {
        scope.launch { controller.check(silent) }
    }

    fun download() {
        if (download?.isActive == true) return
        download = scope.launch { controller.download() }
    }

    fun cancelDownload() {
        download?.cancel()
    }
}
