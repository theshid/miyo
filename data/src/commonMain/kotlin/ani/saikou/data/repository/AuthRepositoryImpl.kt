package ani.saikou.data.repository

import ani.saikou.domain.repository.AuthRepository

/**
 * Pure string-building today — lives in :data/commonMain so it ships as
 * the same impl on Android and (future) iOS. The OAuth client ID is
 * supplied at construction so this module doesn't need to know about
 * `:app`'s `AnilistApi` constant; DI in :app reads it once and hands it
 * in.
 */
class AuthRepositoryImpl(
    private val anilistClientId: Int,
) : AuthRepository {
    override fun getAnilistAuthUrl(): String = "https://anilist.co/api/v2/oauth/authorize?client_id=$anilistClientId&response_type=token"
}
