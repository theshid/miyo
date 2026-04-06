package ani.saikou.domain.repository

import ani.saikou.domain.model.CharacterDetail
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.User

interface AnilistRepository {
    suspend fun getUserData(): User?
    suspend fun getMedia(id: Int): Media?
    suspend fun getCharacter(id: Int): CharacterDetail?
    suspend fun getTrendingAnime(page: Int = 1): List<Media>
    suspend fun getPopularAnime(page: Int = 1): List<Media>
    suspend fun getRecentlyUpdatedAnime(page: Int = 1): List<Media>
    suspend fun getTrendingManga(page: Int = 1): List<Media>
    suspend fun getPopularManga(page: Int = 1): List<Media>
    suspend fun getTrendingNovels(page: Int = 1): List<Media>
    suspend fun getUserAnimeList(status: String): List<Media>
    suspend fun getUserMangaList(status: String): List<Media>
    suspend fun getRecommendations(): List<Media>
    suspend fun search(query: String, type: String, genres: List<String>? = null, sort: String? = null, page: Int = 1): List<Media>
    suspend fun toggleFavorite(id: Int, isAnime: Boolean)
    suspend fun editListEntry(mediaId: Int, progress: Int? = null, score: Int? = null, status: String? = null)
    suspend fun deleteListEntry(listId: Int)

    fun getToken(): String?
    fun saveToken(token: String)
    fun isLoggedIn(): Boolean
}
