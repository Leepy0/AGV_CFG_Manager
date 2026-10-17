package kr.agv.cfgmanager.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** APK 설치 · 브라우저 인텐트 (docs/DEVELOPMENT_PLAN.md 7.5). 파일은 FileProvider 로만 넘긴다. */
object UpdateInstaller {
    private const val APK_MIME = "application/vnd.android.package-archive"

    /** 이 앱이 APK 를 설치할 권한이 있는지 (API 26+). 없으면 [unknownSourcesSettings] 로 안내한다. */
    fun canInstallPackages(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** `cache/updates/` 의 APK 를 설치 화면으로 넘긴다. */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun browserIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
}
