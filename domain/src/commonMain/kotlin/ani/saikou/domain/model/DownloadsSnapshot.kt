package ani.saikou.domain.model

/**
 * Point-in-time state of the user's download library — the shape the
 * downloads-screen use case streams. Bundles the joined manga/chapter
 * tree with the storage + cleanup metrics so the VM gets one snapshot
 * per upstream tick instead of orchestrating four independent calls
 * itself.
 */
data class DownloadsSnapshot(
    val mangaWithDownloads: List<MangaWithDownloads>,
    val totalStorageUsed: Long,
    val freeSpace: Long,
    val readChapters: List<Download>,
) {
    val readChapterCount: Int get() = readChapters.size
    val readChapterBytes: Long get() = readChapters.sumOf { it.fileSizeBytes }
}
