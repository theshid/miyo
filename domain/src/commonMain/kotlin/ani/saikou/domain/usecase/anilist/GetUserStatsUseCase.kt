package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.UserStats
import ani.saikou.domain.repository.AnilistRepository

/**
 * "Show my profile stats" — fetches the signed-in user's anime/manga
 * tallies + genre/score breakdowns. Returns null when the repo can't
 * resolve a user (no token, network failure with no cache); the screen
 * surfaces that as the empty state.
 */
class GetUserStatsUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(): UserStats? = repository.getUserStats()
}
