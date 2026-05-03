package ani.saikou.domain.usecase.news

import ani.saikou.domain.model.AiringScheduleItem
import ani.saikou.domain.repository.NewsRepository

/**
 * "Show what's airing on this day" — `day` is the lowercase English
 * weekday name ("monday".."sunday"). The screen drives the day picker;
 * the VM funnels each pick through this use case.
 */
class GetAiringScheduleUseCase(
    private val repository: NewsRepository,
) {
    suspend operator fun invoke(day: String): List<AiringScheduleItem> = repository.getSchedule(day)
}
