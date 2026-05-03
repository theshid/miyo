package ani.saikou.data.repository

import ani.saikou.data.local.db.ReadingHistoryDao
import ani.saikou.data.local.db.ReadingHistoryEntity
import ani.saikou.data.local.db.WatchHistoryDao
import ani.saikou.data.local.db.WatchHistoryEntity
import ani.saikou.domain.model.ReadingHistoryItem
import ani.saikou.domain.model.WatchHistoryItem
import ani.saikou.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Wraps the watch + reading history DAOs and maps Room entities into the
 * domain item shape. Pure forwarder — the DAOs already return Flow, so
 * each query becomes a `flow.map(::toDomain)` chain.
 */
class HistoryRepositoryImpl(
    private val readingDao: ReadingHistoryDao,
    private val watchDao: WatchHistoryDao,
) : HistoryRepository {
    override fun observeRecentReading(limit: Int): Flow<List<ReadingHistoryItem>> =
        readingDao.getRecent(limit).map { rows -> rows.map(::toDomain) }

    override fun observeContinueWatching(limit: Int): Flow<List<WatchHistoryItem>> =
        watchDao.getInProgress(limit).map { rows -> rows.map(::toDomain) }

    override fun observeRecentWatching(limit: Int): Flow<List<WatchHistoryItem>> =
        watchDao.getRecent(limit).map { rows -> rows.map(::toDomain) }

    override fun observeEpisodesWatchedCount(): Flow<Int> = watchDao.getEpisodesWatchedCount()

    override fun observeChaptersReadCount(): Flow<Int> = readingDao.getChaptersReadCount()

    private fun toDomain(entity: ReadingHistoryEntity) =
        ReadingHistoryItem(
            mangaId = entity.mangaId,
            mangaTitle = entity.mangaTitle,
            coverUrl = entity.coverUrl,
            chapterNumber = entity.chapterNumber,
            chapterName = entity.chapterName,
            chapterId = entity.chapterId,
            sourceId = entity.sourceId,
            sourceName = entity.sourceName,
            lastPage = entity.lastPage,
            totalPages = entity.totalPages,
            lastReadAt = entity.lastReadAt,
        )

    private fun toDomain(entity: WatchHistoryEntity) =
        WatchHistoryItem(
            mediaId = entity.mediaId,
            mediaTitle = entity.mediaTitle,
            coverUrl = entity.coverUrl,
            episodeNumber = entity.episodeNumber,
            sourceSlug = entity.sourceSlug,
            sourceName = entity.sourceName,
            lastPositionMs = entity.lastPositionMs,
            durationMs = entity.durationMs,
            completedEpisodes = entity.completedEpisodes,
            lastWatchedAt = entity.lastWatchedAt,
        )
}
