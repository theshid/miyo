package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.User
import ani.saikou.domain.repository.AnilistRepository

/**
 * "Who's signed in?" — wraps the AniList Viewer query (cached on the
 * repo). Returns null when no token is set or the request failed.
 */
class GetSignedInUserUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(): User? = repository.getUserData()
}
