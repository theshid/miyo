package ani.saikou.domain.usecase.feedback

import ani.saikou.domain.model.FeedbackCategory
import ani.saikou.domain.repository.FeedbackRepository

/**
 * Submit a single piece of user feedback. Today this is a thin pass-
 * through to [FeedbackRepository.submit] — its weight is the contract:
 * the VM speaks in named intents, not repository surfaces. When the
 * submission grows side effects (analytics breadcrumb, retry-with-
 * backoff, fan-out to a second sink) they belong here, not in the VM.
 */
class SubmitFeedbackUseCase(
    private val repository: FeedbackRepository,
) {
    suspend operator fun invoke(
        category: FeedbackCategory,
        message: String,
    ): Result<Unit> = repository.submit(category, message)
}
