package ani.saikou.domain.model

/**
 * Result of [ani.saikou.domain.repository.DownloadRepository.evictReadChapters].
 * UI surfaces the totals back to the user as confirmation ("Freed 1.2 GB
 * across 47 chapters").
 */
data class EvictionSummary(
    val chaptersRemoved: Int,
    val bytesFreed: Long,
)
