package ani.saikou.data.local.downloads

import ani.saikou.data.local.db.DownloadDao
import ani.saikou.domain.util.formatBytes

/**
 * Estimates how many bytes `count` chapters of a given manga will take on
 * disk, using real per-series averages when available, then a global average,
 * then a conservative hardcoded default.
 *
 * The fallback matters only for a user's very first download. Everything
 * after that is driven by measured bytes written by [MangaDownloadManager].
 */
class ChapterSizeEstimator(
    private val dao: DownloadDao,
) {
    companion object {
        // Typical B&W manga chapter (~25 pages × ~400 KB) lands near this.
        // Color webtoons are 3-5× bigger but quickly replace this value with
        // their own measured average after a single completed download.
        const val DEFAULT_CHAPTER_BYTES = 10L * 1024 * 1024

        /**
         * Human-readable "≈ 42 MB" or "≈ 1.2 GB" form. Delegates to the
         * domain util — kept here as a static for backwards compatibility
         * with existing call sites.
         */
        fun format(bytes: Long): String = formatBytes(bytes)
    }

    /**
     * @return estimated bytes for `count` chapters, or the conservative
     *   fallback × count if we have no history at all.
     */
    suspend fun estimateBytes(
        mangaId: Int,
        count: Int,
    ): Long {
        if (count <= 0) return 0L
        val perChapter =
            dao.getAverageSizeForManga(mangaId)?.toLong()
                ?: dao.getGlobalAverageSize()?.toLong()
                ?: DEFAULT_CHAPTER_BYTES
        return perChapter * count
    }
}
