package dev.munote.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import dev.munote.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class PdfSession private constructor(
    val file: File,
    val fingerprint: String,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : AutoCloseable {
    val pageCount: Int = renderer.pageCount
    private val lock = Mutex()
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        val width = targetWidthPx.coerceIn(160, 2400)
        val key = "${index}:${width}"
        cache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }

        lock.withLock {
            cache.get(key)?.takeIf { !it.isRecycled }?.let { return@withLock it }
            val page = renderer.openPage(index)
            try {
                val ratio = page.height.toFloat() / page.width.toFloat()
                val height = (width * ratio).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(key, bmp)
                bmp
            } finally {
                page.close()
            }
        }
    }

    fun clearRenderCache() {
        cache.evictAll()
    }

    override fun close() {
        cache.evictAll()
        renderer.close()
        descriptor.close()
    }

    companion object {
        suspend fun open(context: Context, uri: Uri): PdfSession = withContext(Dispatchers.IO) {
            val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
            val temp = File(docsDir, "import-${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { context.getString(R.string.error_input_open) }
                FileOutputStream(temp).use { output -> input.copyTo(output, 1024 * 1024) }
            }
            val fingerprint = sha256(temp)
            val finalFile = File(docsDir, "${fingerprint}.pdf")
            if (finalFile.exists()) temp.delete() else temp.renameTo(finalFile)
            openFile(finalFile, fingerprint)
        }

        suspend fun openStored(
            context: Context,
            fingerprint: String,
        ): PdfSession = withContext(Dispatchers.IO) {
            val file = File(File(context.filesDir, "documents"), "${fingerprint}.pdf")
            require(file.exists()) { context.getString(R.string.error_local_pdf_missing) }
            openFile(file, fingerprint)
        }

        private fun openFile(file: File, fingerprint: String): PdfSession {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return PdfSession(file, fingerprint, pfd, PdfRenderer(pfd))
        }

        private fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(1024 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
