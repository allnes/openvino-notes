package com.itlab.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val folderId: String? = null,
    val contentItems: List<ContentItem> = emptyList(),
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val tags: Set<String> = emptySet(),
    val isFavorite: Boolean = false,
    val summary: String? = null,
)

@Serializable
data class DataSource(
    val localPath: String? = null,
    val remoteUrl: String? = null,
) {
    val displayPath: String? get() = localPath ?: remoteUrl
}

@Serializable
sealed class ContentItem {
    @Serializable
    data class Text(
        val text: String,
        val format: TextFormat = TextFormat.PLAIN,
    ) : ContentItem()

    @Serializable
    data class Image(
        val source: DataSource,
        val mimeType: String,
        val width: Int? = null,
        val height: Int? = null,
    ) : ContentItem()

    @Serializable
    data class File(
        val source: DataSource,
        val mimeType: String,
        val name: String,
        val size: Long? = null,
    ) : ContentItem()

    @Serializable
    data class Link(
        val url: String,
        val title: String? = null,
    ) : ContentItem()
}

@Serializable
enum class TextFormat {
    PLAIN,
    MARKDOWN,
    HTML,
}
