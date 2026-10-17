package kr.agv.cfgmanager.core.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** 이 빌드의 정보. 앱이 BuildConfig 와 Build.VERSION 에서 채운다. */
data class BuildInfo(
    val versionCode: Int,
    val channel: String,
    val deviceSdk: Int,
)

/** 업데이트 화면 상태. 정보 화면과 자동 확인이 같은 상태를 본다. */
sealed interface UpdateState {
    /** 아직 확인하지 않았다. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** 최신이다. [version] 은 확인된 최신 정식 버전. */
    data class UpToDate(val version: UpdateVersion) : UpdateState

    /**
     * 업데이트가 있다. [installableHere] 가 false 면 이 휴대폰의 Android 가 낮아 설치할 수 없다.
     * [overwriteNote] 가 true 면 dev · local 빌드이며 정식 APK 로 덮어 설치된다는 안내를 붙인다.
     */
    data class Available(
        val release: LatestRelease,
        val installableHere: Boolean,
        val overwriteNote: Boolean,
    ) : UpdateState

    /** 받는 중. [total] 이 -1 이면 전체 크기를 모른다. */
    data class Downloading(val release: LatestRelease, val received: Long, val total: Long) : UpdateState {
        /** 0~100. 전체를 모르면 null. */
        val percent: Int? get() = if (total > 0L) ((received * 100L) / total).toInt().coerceIn(0, 100) else null
    }

    /** 받아서 sha256 을 맞췄다. 설치 인텐트에 [apk] 를 넘긴다. */
    data class Ready(val release: LatestRelease, val apk: File) : UpdateState

    /** 실패. [release] 가 있으면 브라우저 받기 안내를 함께 낸다. */
    data class Failed(val message: String, val release: LatestRelease?) : UpdateState
}

/**
 * 업데이트 상태 기계 (docs/DEVELOPMENT_PLAN.md 7.5 · 12장). 확인 · 받기는 한 번에 하나만 돈다.
 * 자동 확인([check] 의 silent)은 실패해도 화면에 아무것도 내지 않고 이전 상태로 되돌린다.
 */
class UpdateController(
    private val checker: UpdateChecker,
    private val downloader: UpdateDownloader,
    private val build: BuildInfo,
    private val journal: (String) -> Unit = {},
) {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    private val gate = Mutex()

    /** 최신 릴리스를 확인하고 최종 상태를 돌려준다. 받는 중이면 아무것도 하지 않는다. */
    suspend fun check(silent: Boolean): UpdateState {
        val before = gate.withLock {
            val current = mutableState.value
            if (current is UpdateState.Checking || current is UpdateState.Downloading) return current
            mutableState.value = UpdateState.Checking
            current
        }
        val next = try {
            val release = checker.latest()
            journal("확인: 최신 ${release.version} (${release.source.name})")
            if (release.versionCode > build.versionCode) {
                UpdateState.Available(
                    release = release,
                    installableHere = release.minSdk?.let { it <= build.deviceSdk } ?: true,
                    overwriteNote = UpdateChannel.isPreview(build.channel),
                )
            } else {
                UpdateState.UpToDate(release.version)
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = before
            throw cancelled
        } catch (failure: Exception) {
            journal("확인 실패: ${failure.javaClass.simpleName}")
            if (silent) before else UpdateState.Failed(messageOf(failure), null)
        }
        mutableState.value = next
        return next
    }

    /** [UpdateState.Available] 의 릴리스를 받아 검증한다. sha256 이 없으면 아무것도 하지 않는다. */
    suspend fun download(): UpdateState {
        val available = gate.withLock {
            val current = mutableState.value as? UpdateState.Available ?: return mutableState.value
            if (!current.release.installableInApp || !current.installableHere) return current
            mutableState.value = UpdateState.Downloading(current.release, 0L, current.release.size ?: -1L)
            current
        }
        val release = available.release
        val next = try {
            val apk = downloader.download(release) { received, total ->
                mutableState.value = UpdateState.Downloading(release, received, total)
            }
            journal("받기 완료: ${release.version}")
            UpdateState.Ready(release, apk)
        } catch (cancelled: CancellationException) {
            mutableState.value = available
            throw cancelled
        } catch (failure: Exception) {
            journal("받기 실패: ${failure.javaClass.simpleName}")
            UpdateState.Failed(messageOf(failure), release)
        }
        mutableState.value = next
        return next
    }

    private fun messageOf(failure: Exception): String = when (failure) {
        is UpdateFailure -> failure.message ?: "업데이트를 확인하지 못했습니다."
        else -> "인터넷에 연결되지 않아 확인하지 못했습니다. 연결을 확인하세요."
    }
}
