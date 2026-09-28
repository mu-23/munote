package dev.munote.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class StickerItem(
    val id: String,
    val fileName: String,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val createdAt: Long,
)

@Serializable
private data class StickerDocument(
    val stickers: List<StickerItem> = emptyList(),
)

class StickerStore(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val root = File(context.filesDir, "stickers").apply { mkdirs() }
    private val mediaDir = File(root, "media").apply { mkdirs() }
    private val indexFile = File(root, "index.json")
    private val stickers = mutableListOf<StickerItem>()

    init {
        if (indexFile.exists()) {
            runCatching {
                stickers += json.decodeFromString<StickerDocument>(
                    indexFile.readText()
                ).stickers.filter { File(mediaDir, it.fileName).exists() }
            }
        }
    }

    fun items(): List<StickerItem> =
        stickers.sortedByDescending { it.createdAt }

    fun fileFor(sticker: StickerItem): File =
        File(mediaDir, sticker.fileName)

    suspend fun saveFromImage(
        source: File,
        image: PageImageNote,
    ): StickerItem = withContext(Dispatchers.IO) {
        require(source.exists()) { "Image file is missing" }

        val id = UUID.randomUUID().toString()
        val extension = source.extension.ifBlank { "img" }
        val fileName = "$id.$extension"
        val target = File(mediaDir, fileName)
        val temp = File(mediaDir, "$fileName.tmp")

        source.inputStream().use { input ->
            temp.outputStream().use { output ->
                input.copyTo(output, 512 * 1024)
            }
        }
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Unable to save sticker" }

        val sticker = StickerItem(
            id = id,
            fileName = fileName,
            cropLeft = image.cropLeft,
            cropTop = image.cropTop,
            cropRight = image.cropRight,
            cropBottom = image.cropBottom,
            createdAt = System.currentTimeMillis(),
        )
        stickers += sticker
        persist()
        sticker
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val sticker = stickers.firstOrNull { it.id == id } ?: return@withContext
        stickers.removeAll { it.id == id }
        runCatching { fileFor(sticker).delete() }
        persist()
    }

    suspend fun loadBitmap(
        sticker: StickerItem,
        targetWidthPx: Int = 480,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val source = fileFor(sticker)
        if (!source.exists()) return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return@withContext null

        val left = (bitmap.width * sticker.cropLeft).toInt()
            .coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * sticker.cropTop).toInt()
            .coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * sticker.cropRight).toInt()
            .coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * sticker.cropBottom).toInt()
            .coerceIn(top + 1, bitmap.height)

        if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }
    }

    private fun persist() {
        val temp = File(root, "index.json.tmp")
        temp.writeText(json.encodeToString(StickerDocument(stickers.toList())))
        if (indexFile.exists()) indexFile.delete()
        temp.renameTo(indexFile)
    }
}
