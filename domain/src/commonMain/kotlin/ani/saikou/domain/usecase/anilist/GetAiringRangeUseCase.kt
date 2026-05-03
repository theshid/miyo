package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.AiringEntry
import ani.saikou.domain.repository.AnilistRepository

/**
 * "What's airing between these two epoch seconds" — used by the
 * seasonal calendar's weekly view. Distinct from `news.GetAiringSchedule
 * UseCase`, which is a Jikan-backed by-day-of-week lookup; this one
 * hits AniList with an inclusive epoch range and returns per-episode
 * [AiringEntry] rows.
 */
class GetAiringRangeUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(
        weekStart: Long,
        weekEnd: Long,
        page: Int = 1,
    ): List<AiringEntry> = repository.getAiringSchedule(weekStart, weekEnd, page)
}
