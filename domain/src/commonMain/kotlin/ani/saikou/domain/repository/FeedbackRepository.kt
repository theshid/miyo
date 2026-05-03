package ani.saikou.domain.repository

import ani.saikou.domain.model.FeedbackCategory

/**
 * Domain entry point for sending user feedback. VMs depend on this and
 * never see the underlying [ani.saikou.domain.source.FeedbackService] or
 * its platform impl — that means a future swap of the outbound transport
 * (Discord webhook → Sentry user-feedback API → email relay) is a
 * one-place change in the impl, with VMs and the screen unaffected.
 */
interface FeedbackRepository {
    /**
     * Returns [Result.success] (Unit) on a 2xx; [Result.failure] with a
     * UI-ready message in `Throwable.message` otherwise. Never throws.
     */
    suspend fun submit(
        category: FeedbackCategory,
        message: String,
    ): Result<Unit>
}
