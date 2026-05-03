package ani.saikou.data.repository

import ani.saikou.data.local.db.ActivityEventDao
import ani.saikou.data.local.db.ActivityEventEntity
import ani.saikou.domain.model.ActivityEvent
import ani.saikou.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ActivityRepositoryImpl(
    private val dao: ActivityEventDao,
) : ActivityRepository {
    override fun observeActivitySince(sinceEpochMs: Long): Flow<List<ActivityEvent>> =
        dao.getSince(sinceEpochMs).map { rows -> rows.map(::toDomain) }

    private fun toDomain(entity: ActivityEventEntity) =
        ActivityEvent(
            timestampMs = entity.timestampMs,
            type = entity.type,
            mediaId = entity.mediaId,
            mediaTitle = entity.mediaTitle,
            coverUrl = entity.coverUrl,
            episodeNumber = entity.episodeNumber,
            chapterNumber = entity.chapterNumber,
        )
}
