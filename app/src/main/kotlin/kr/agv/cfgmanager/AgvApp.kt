package kr.agv.cfgmanager

import android.app.Application

/** 앱 진입점. 연결 세션을 쥔 [AppContainer] 를 프로세스 수명 동안 하나 만든다. */
class AgvApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
