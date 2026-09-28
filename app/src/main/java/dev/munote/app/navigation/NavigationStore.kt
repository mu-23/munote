package dev.munote.app.navigation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class PageLink(
    val id: String,
    val sourcePage: Int,
    val targetPage: Int,
    val label: String,
    val x: Float = 0.06f,
    val y: Float = 0.06f,
    val width: Float = 0.24f,
)

@Serializable
data class OutlineEntry(
    val id: String,
    val title: String,
    val pageIndex: Int,
    val createdAt: Long,
)

@Serializable
private data class NavigationDocument(
    val links: List<PageLink> = emptyList(),
    val outline: List<OutlineEntry> = emptyList(),
)

class NavigationStore(
    context: Context,
    fingerprint: String,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val file = File(
        File(context.filesDir, "navigation").apply { mkdirs() },
        "${fingerprint}.json"
    )
    private val links = mutableListOf<PageLink>()
    private val outline = mutableListOf<OutlineEntry>()

    init {
        if (file.exists()) {
            runCatching {
                val document = json.decodeFromString<NavigationDocument>(file.readText())
                links += document.links
                outline += document.outline
            }
        }
    }

    fun linksForPage(pageIndex: Int): List<PageLink> =
        links.filter { it.sourcePage == pageIndex }

    fun allOutline(): List<OutlineEntry> =
        outline.sortedWith(
            compareBy<OutlineEntry> { it.pageIndex }
                .thenBy { it.createdAt }
        )

    suspend fun addLink(
        sourcePage: Int,
        targetPage: Int,
        label: String,
    ): PageLink = withContext(Dispatchers.IO) {
        val pageLinks = links.count { it.sourcePage == sourcePage }
        val link = PageLink(
            id = UUID.randomUUID().toString(),
            sourcePage = sourcePage,
            targetPage = targetPage,
            label = label.trim().ifBlank { "→ ${targetPage + 1}" },
            x = 0.06f,
            y = (0.06f + (pageLinks % 8) * 0.065f).coerceAtMost(0.86f),
        )
        links += link
        persist()
        link
    }

    suspend fun updateLink(link: PageLink) = withContext(Dispatchers.IO) {
        val index = links.indexOfFirst { it.id == link.id }
        val normalized = link.copy(
            x = link.x.coerceIn(0f, (1f - link.width).coerceAtLeast(0f)),
            y = link.y.coerceIn(0f, 0.94f),
            width = link.width.coerceIn(0.12f, 0.60f),
        )
        if (index >= 0) links[index] = normalized else links += normalized
        persist()
    }

    suspend fun deleteLink(id: String) = withContext(Dispatchers.IO) {
        links.removeAll { it.id == id }
        persist()
    }

    suspend fun addOutline(pageIndex: Int, title: String): OutlineEntry =
        withContext(Dispatchers.IO) {
            val entry = OutlineEntry(
                id = UUID.randomUUID().toString(),
                title = title.trim().ifBlank { "Page ${pageIndex + 1}" },
                pageIndex = pageIndex,
                createdAt = System.currentTimeMillis(),
            )
            outline += entry
            persist()
            entry
        }

    suspend fun deleteOutline(id: String) = withContext(Dispatchers.IO) {
        outline.removeAll { it.id == id }
        persist()
    }

    suspend fun deletePage(pageIndex: Int) = withContext(Dispatchers.IO) {
        links.removeAll { it.sourcePage == pageIndex || it.targetPage == pageIndex }
        for (index in links.indices) {
            val link = links[index]
            links[index] = link.copy(
                sourcePage = if (link.sourcePage > pageIndex) link.sourcePage - 1 else link.sourcePage,
                targetPage = if (link.targetPage > pageIndex) link.targetPage - 1 else link.targetPage,
            )
        }

        outline.removeAll { it.pageIndex == pageIndex }
        for (index in outline.indices) {
            val entry = outline[index]
            if (entry.pageIndex > pageIndex) {
                outline[index] = entry.copy(pageIndex = entry.pageIndex - 1)
            }
        }
        persist()
    }

    suspend fun duplicatePage(pageIndex: Int) = withContext(Dispatchers.IO) {
        val oldLinks = links.toList()
        for (index in links.indices) {
            val link = links[index]
            links[index] = link.copy(
                sourcePage = if (link.sourcePage > pageIndex) link.sourcePage + 1 else link.sourcePage,
                targetPage = if (link.targetPage > pageIndex) link.targetPage + 1 else link.targetPage,
            )
        }
        val duplicatedLinks = oldLinks
            .filter { it.sourcePage == pageIndex }
            .map { link ->
                link.copy(
                    id = UUID.randomUUID().toString(),
                    sourcePage = pageIndex + 1,
                    targetPage = if (link.targetPage > pageIndex) link.targetPage + 1 else link.targetPage,
                    y = (link.y + 0.02f).coerceAtMost(0.94f),
                )
            }
        links += duplicatedLinks

        for (index in outline.indices) {
            val entry = outline[index]
            if (entry.pageIndex > pageIndex) {
                outline[index] = entry.copy(pageIndex = entry.pageIndex + 1)
            }
        }
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

        for (index in links.indices) {
            val link = links[index]
            links[index] = link.copy(
                sourcePage = remap(link.sourcePage),
                targetPage = remap(link.targetPage),
            )
        }
        for (index in outline.indices) {
            val entry = outline[index]
            outline[index] = entry.copy(pageIndex = remap(entry.pageIndex))
        }
        persist()
    }

    private fun persist() {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(
            json.encodeToString(
                NavigationDocument(
                    links = links.toList(),
                    outline = outline.toList(),
                )
            )
        )
        if (file.exists()) file.delete()
        temp.renameTo(file)
    }
}
