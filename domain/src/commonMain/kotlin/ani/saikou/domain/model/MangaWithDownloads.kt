package ani.saikou.domain.model

/**
 * A [DownloadedManga] paired with its downloaded chapters. The downloads
 * library is naturally hierarchical (one manga → many chapters); this
 * projection keeps that structure visible to the VM without making the
 * VM do the join itself.
 */
data class MangaWithDownloads(
    val manga: DownloadedManga,
    val chapters: List<Download>,
)
