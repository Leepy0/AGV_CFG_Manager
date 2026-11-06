package kr.agv.cfgmanager.pkg

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kr.agv.cfgmanager.core.pkg.PkgFiles
import java.io.File
import java.io.IOException

/**
 * 고른 폴더 아래에 [folder] 하위 폴더를 만들고 `.out` · `.map` 두 파일을 쓴다 (SAF, 권한은 이번 한 번만).
 * 블로킹 I/O 이므로 IO 디스패처에서 부른다. 실패하면 만들다 만 폴더를 지우고 [IOException] 을 던진다.
 */
object PkgSaver {
    private const val MIME = "application/octet-stream"

    fun save(resolver: ContentResolver, tree: Uri, folder: String, files: PkgFiles) {
        var created: Uri? = null
        try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val dir = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, folder)
                ?: throw IOException("폴더를 만들지 못함")
            created = dir
            for (file in listOf(files.out, files.map)) copyInto(resolver, dir, file)
        } catch (e: IOException) {
            discard(resolver, created)
            throw e
        } catch (e: RuntimeException) {
            // 권한 취소 · 잘못된 주소 같은 시스템 예외도 저장 실패로 같게 다룬다
            discard(resolver, created)
            throw IOException("저장 실패: ${e.javaClass.simpleName}", e)
        }
    }

    private fun copyInto(resolver: ContentResolver, dir: Uri, source: File) {
        val target = DocumentsContract.createDocument(resolver, dir, MIME, source.name)
            ?: throw IOException("파일을 만들지 못함")
        val out = resolver.openOutputStream(target, "w") ?: throw IOException("파일을 열지 못함")
        out.use { sink -> source.inputStream().use { it.copyTo(sink) } }
    }

    private fun discard(resolver: ContentResolver, uri: Uri?) {
        if (uri == null) return
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (_: Exception) {
            // 지우기 실패는 원래 오류를 덮지 않는다
        }
    }
}
