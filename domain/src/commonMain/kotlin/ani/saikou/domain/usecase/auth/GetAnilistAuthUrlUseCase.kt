package ani.saikou.domain.usecase.auth

import ani.saikou.domain.repository.AuthRepository

/**
 * Returns the AniList OAuth URL the login screen launches. Wraps
 * [AuthRepository.getAnilistAuthUrl] so the VM speaks a named intent
 * rather than calling the repository directly. When the auth flow grows
 * pre-launch concerns (PKCE challenge, analytics breadcrumb, A/B-tested
 * client id), they land here.
 */
class GetAnilistAuthUrlUseCase(
    private val repository: AuthRepository,
) {
    operator fun invoke(): String = repository.getAnilistAuthUrl()
}
