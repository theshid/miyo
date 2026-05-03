package ani.saikou.domain.repository

import ani.saikou.domain.model.AiringEntry
import ani.saikou.domain.model.AnilistFailure
import ani.saikou.domain.model.CharacterDetail
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.User
import ani.saikou.domain.model.UserStats
import kotlinx.coroutines.flow.StateFlow

interface AnilistRepository {
    /**
     * Side-channel report of the last network/server failure observed by
     * the underlying transport (cleared on the next successful read).
     * Discovery screens use this together with "all sections empty" to
     * tell a real outage from a legitimately empty result.
     */
    val lastFailure: StateFlow<AnilistFailure?>

    suspend fun getUserStats(): UserStats?

    suspend fun getUserData(): User?

    suspend fun getMedia(id: Int): Media?

    suspend fun getCharacter(id: Int): CharacterDetail?

    suspend fun getTrendingAnime(page: Int = 1): List<Media>

    suspend fun getPopularAnime(page: Int = 1): List<Media>

    suspend fun getRecentlyUpdatedAnime(page: Int = 1): List<Media>

    suspend fun getTrendingManga(page: Int = 1): List<Media>

    suspend fun getPopularManga(page: Int = 1): List<Media>

    suspend fun getRecentlyUpdatedManga(page: Int = 1): List<Media>

    suspend fun getTrendingNovels(page: Int = 1): List<Media>

    suspend fun getUserAnimeList(status: String): List<Media>

    suspend fun getUserMangaList(status: String): List<Media>

    suspend fun getUserFavorites(type: String): List<Media>

    suspend fun getRecommendations(): List<Media>

    suspend fun getSeasonalAnime(
        season: String,
        year: Int,
        page: Int = 1,
    ): List<Media>

    suspend fun getAiringSchedule(
        weekStart: Long,
        weekEnd: Long,
        page: Int = 1,
    ): List<AiringEntry>

    suspend fun search(
        query: String,
        type: String,
        genres: List<String>? = null,
        sort: String? = null,
        page: Int = 1,
    ): List<Media>

    suspend fun toggleFavorite(
        id: Int,
        isAnime: Boolean,
    )

    suspend fun editListEntry(
        mediaId: Int,
        progress: Int? = null,
        score: Int? = null,
        status: String? = null,
    )

    suspend fun deleteListEntry(listId: Int)

    fun getToken(): String?

    fun saveToken(token: String)

    fun isLoggedIn(): Boolean
}
