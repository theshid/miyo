package ani.saikou.domain.source

import ani.saikou.domain.model.FeedbackCategory

/**
 * Outbound feedback channel — POSTs user-submitted reports to whatever
 * sink the platform impl uses. Stateless. Wrapped by [ani.saikou.domain.
 * repository.FeedbackRepository] so VMs depend on the repository surface
 * rather than this lower-level integration directly.
 *
 * Returns Kotlin's [Result] — domain-side, no service-specific sealed
 * types leak into callers. Failure carries a human-readable reason
 * suitable for surfacing in the UI.
 */
interface FeedbackService {
    suspend fun submit(
        category: FeedbackCategory,
        message: String,
    ): Result<Unit>
}
