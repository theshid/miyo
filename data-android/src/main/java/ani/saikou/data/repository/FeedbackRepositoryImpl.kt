package ani.saikou.data.repository

import ani.saikou.domain.model.FeedbackCategory
import ani.saikou.domain.repository.FeedbackRepository
import ani.saikou.domain.source.FeedbackService

/**
 * Thin pass-through today — the value is the architectural boundary, not
 * any local logic. VMs depend on [FeedbackRepository], not on the lower-
 * level [FeedbackService]; that means any future cross-cutting concern
 * (caching, retry, fan-out to a second sink) lands here without touching
 * the presentation layer.
 */
class FeedbackRepositoryImpl(
    private val service: FeedbackService,
) : FeedbackRepository {
    override suspend fun submit(
        category: FeedbackCategory,
        message: String,
    ): Result<Unit> = service.submit(category, message)
}
