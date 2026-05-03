package ani.saikou.domain.repository

/**
 * Auth-flow-specific contract — separate from [AnilistRepository] (which
 * deals with already-authed AniList queries) so the auth lifecycle has
 * room to grow (logout, token refresh, multi-provider) without bloating
 * the data-fetching surface. VMs reach this through use cases only.
 */
interface AuthRepository {
    /**
     * Builds the AniList OAuth authorize URL the screen launches via
     * Custom Tabs. Implicit-flow (`response_type=token`) — the access
     * token comes back in the redirect fragment, intercepted by
     * `LoginCallbackActivity`.
     */
    fun getAnilistAuthUrl(): String
}
