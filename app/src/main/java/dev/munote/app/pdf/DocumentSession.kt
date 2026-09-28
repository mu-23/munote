package dev.munote.app.pdf

import android.graphics.Bitmap
import kotlinx.serialization.Serializable

@Serializable
enum class DocumentKind {
    PDF,
    NOTEBOOK,
}

@Serializable
enum class PageTemplate {
    BLANK,
    RULED,
    GRID,
    DOT,
}

interface DocumentSession : AutoCloseable {
    val fingerprint: String
    val pageCount: Int

    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap
    fun clearRenderCache()
}
