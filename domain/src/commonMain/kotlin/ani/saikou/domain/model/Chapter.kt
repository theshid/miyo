package ani.saikou.domain.model

data class Chapter(
    val id: String,
    val number: Float,
    val name: String,
)

data class MangaPage(
    val index: Int,
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap(),
)

data class MangaSource(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    /**
     * Total chapter count the SOURCE believes the series has (from its own
     * metadata, e.g. MangaDex's `attributes.lastChapter`). Used to detect
     * licensed/partial listings where the source catalogs the title but only
     * hosts a handful of chapters. `null` when the source doesn't expose this.
     */
    val totalChapterHint: Int? = null,
)
