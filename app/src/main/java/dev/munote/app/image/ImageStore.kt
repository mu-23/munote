package dev.munote.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Serializable
data class PageImageNote(
    val id: String,
    val fileName: String,
    val x: Float = 0.16f,
    val y: Float = 0.18f,
    val width: Float = 0.48f,
    val height: Float = 0.34f,
    val rotationDegrees: Float = 0f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val locked: Boolean = false,
)

@Serializable
private data class ImageDocument(
    val pages: Map<Int, List<PageImageNote>> = emptyMap(),
)

class ImageStore(
    private val context: Context,
    private val fingerprint: String,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mediaDir = File(
        File(context.filesDir, "images").apply { mkdirs() },
        fingerprint
    ).apply { mkdirs() }
    private val file = File(
        File(context.filesDir, "image-notes").apply { mkdirs() },
        "$fingerprint.json"
    )
    private val pages = linkedMapOf<Int, MutableList<PageImageNote>>()

    init {
        if (file.exists()) {
            runCatching {
                json.decodeFromString<ImageDocument>(file.readText()).pages.forEach { (page, images) ->
                    pages[page] = images.toMutableList()
                }
            }
        }
    }

    fun page(index: Int): List<PageImageNote> = pages[index]?.toList().orEmpty()

    suspend fun add(index: Int, uri: Uri): PageImageNote = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val fileName = "$id.img"
        val target = File(mediaDir, fileName)
        val temp = File(mediaDir, "$fileName.tmp")

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open image" }
            FileOutputStream(temp).use { output ->
                input.copyTo(output, 512 * 1024)
            }
        }
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Unable to save image" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        val aspect = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
        } else {
            1f
        }
        val width = 0.48f
        val pageAspect = 1240f / 1754f
        val height = (width * pageAspect / aspect).coerceIn(0.12f, 0.58f)

        val image = PageImageNote(
            id = id,
            fileName = fileName,
            width = width,
            height = height,
        )
        pages.getOrPut(index) { mutableListOf() }.add(image)
        persist()
        image
    }

    suspend fun update(index: Int, image: PageImageNote) = withContext(Dispatchers.IO) {
        val list = pages.getOrPut(index) { mutableListOf() }
        val position = list.indexOfFirst { it.id == image.id }
        val normalized = image.copy(
            x = image.x.coerceIn(0f, 0.95f),
            y = image.y.coerceIn(0f, 0.95f),
            width = image.width.coerceIn(0.06f, 0.96f),
            height = image.height.coerceIn(0.06f, 0.96f),
            cropLeft = image.cropLeft.coerceIn(0f, 0.94f),
            cropTop = image.cropTop.coerceIn(0f, 0.94f),
            cropRight = image.cropRight.coerceIn(0.06f, 1f),
            cropBottom = image.cropBottom.coerceIn(0.06f, 1f),
        )
        if (position >= 0) list[position] = normalized else list.add(normalized)
        persist()
    }

    suspend fun delete(index: Int, id: String) = withContext(Dispatchers.IO) {
        val list = pages[index] ?: return@withContext
        val image = list.firstOrNull { it.id == id } ?: return@withContext
        list.removeAll { it.id == id }
        if (list.isEmpty()) pages.remove(index)
        if (pages.values.none { page -> page.any { it.fileName == image.fileName } }) {
            runCatching { File(mediaDir, image.fileName).delete() }
        }
        persist()
    }

    fun imageFile(image: PageImageNote): File = File(mediaDir, image.fileName)

    suspend fun loadBitmap(
        image: PageImageNote,
        targetWidthPx: Int = 1200,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val source = imageFile(image)
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

        val left = (bitmap.width * image.cropLeft).toInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * image.cropTop).toInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * image.cropRight).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * image.cropBottom).toInt().coerceIn(top + 1, bitmap.height)

        if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }
    }

    suspend fun deletePage(index: Int) = withContext(Dispatchers.IO) {
        val removed = pages[index].orEmpty().toList()
        val remapped = linkedMapOf<Int, MutableList<PageImageNote>>()
        pages.entries.sortedBy { it.key }.forEach { (page, images) ->
            when {
                page < index -> remapped[page] = images.toMutableList()
                page > index -> remapped[page - 1] = images.toMutableList()
            }
        }
        pages.clear()
        pages.putAll(remapped)
        removed.forEach { image ->
            if (pages.values.none { list -> list.any { it.fileName == image.fileName } }) {
                runCatching { File(mediaDir, image.fileName).delete() }
            }
        }
        persist()
    }

    suspend fun duplicatePage(index: Int) = withContext(Dispatchers.IO) {
        val source = page(index).map { original ->
            val newId = UUID.randomUUID().toString()
            val extension = original.fileName.substringAfterLast('.', "img")
            val newName = "$newId.$extension"
            val sourceFile = File(mediaDir, original.fileName)
            val targetFile = File(mediaDir, newName)
            if (sourceFile.exists()) sourceFile.copyTo(targetFile, overwrite = true)
            original.copy(id = newId, fileName = newName)
        }
        val remapped = linkedMapOf<Int, MutableList<PageImageNote>>()
        pages.entries.sortedBy { it.key }.forEach { (page, images) ->
            remapped[if (page > index) page + 1 else page] = images.toMutableList()
        }
        if (source.isNotEmpty()) remapped[index + 1] = source.toMutableList()
        pages.clear()
        pages.putAll(remapped.toSortedMap())
        persist()
    }

    suspend fun movePage(fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        if (fromIndex == toIndex) return@withContext
        fun remap(page: Int): Int = when {
            page == fromIndex -> toIndex
            fromIndex < toIndex && page in (fromIndex + 1)..toIndex -> page - 1
            fromIndex > toIndex && page in toIndex until fromIndex -> page + 1
            else -> page
        }
        val moved = linkedMapOf<Int, MutableList<PageImageNote>>()
        pages.forEach { (page, images) ->
            moved[remap(page)] = images.toMutableList()
        }
        pages.clear()
        pages.putAll(moved.toSortedMap())
        persist()
    }

    private fun persist() {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(ImageDocument(pages.mapValues { it.value.toList() })))
        if (file.exists()) file.delete()
        temp.renameTo(file)
    }
}
