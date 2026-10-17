package kr.agv.cfgmanager.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** 공유 시트로 넘길 임시 파일 (cache/share/, FileProvider 로만 노출). */
object ShareFiles {

    fun writeText(context: Context, name: String, text: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(text, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun sendIntent(uri: Uri, mimeType: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** 공유 시트 인텐트. 파일 쓰기(writeText)는 IO 스레드에서, startActivity 는 메인에서 한다. */
    fun chooser(uri: Uri, mimeType: String, title: String): Intent = Intent.createChooser(sendIntent(uri, mimeType), title)
}
