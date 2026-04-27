package ani.saikou.data.local.downloads

import ani.saikou.data.local.db.DownloadDao

/**
 * Estimates how many bytes `count` chapters of a given manga will take on
 * disk, using real per-series averages when available, then a global average,
 * then a conservative hardcoded default.
 *
 * The fallback matters only for a user's very first download. Everything
 * after that is driven by measured bytes written by [MangaDownloadManager].
 */
class ChapterSizeEstimator(private val dao: DownloadDao) {

    companion object {
        // Typical B&W manga chapter (~25 pages × ~400 KB) lands near this.
        // Color webtoons are 3-5× bigger but quickly replace this value with
        // their own measured average after a single completed download.
        const val DEFAULT_CHAPTER_BYTES = 10L * 1024 * 1024
    }

    /**
     * @return estimated bytes for `count` chapters, or the conservative
     *   fallback × count if we have no history at all.
     */
    suspend fun estimateBytes(mangaId: Int, count: Int): Long {
        if (count <= 0) return 0L
        val perChapter = dao.getAverageSizeForManga(mangaId)?.toLong()
            ?: dao.getGlobalAverageSize()?.toLong()
            ?: DEFAULT_CHAPTER_BYTES
        return perChapter * count
    }

    /**
     * Human-readable "≈ 42 MB" or "≈ 1.2 GB" form.
     */
    fun format(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "%.1f GB".format(gb)
            mb >= 10.0 -> "%.0f MB".format(mb)
            mb >= 1.0 -> "%.1f MB".format(mb)
            else -> "%.0f KB".format(kb)
        }
    }
}
