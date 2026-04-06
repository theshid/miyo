package ani.saikou.data.repository

import ani.saikou.data.local.TokenStorage
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.remote.AnilistQueries
import ani.saikou.data.remote.MediaParser
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.User
import ani.saikou.domain.repository.AnilistRepository
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AnilistRepositoryImpl(
    private val api: AnilistApi,
    private val tokenStorage: TokenStorage,
) : AnilistRepository {

    private var cachedUser: User? = null

    // ── Auth ──────────────────────────────────────────────────

    override fun getToken(): String? = tokenStorage.getToken()
    override fun saveToken(token: String) = tokenStorage.saveToken(token)
    override fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()

    // ── User ──────────────────────────────────────────────────

    override suspend fun getUserData(): User? {
        cachedUser?.let { return it }
        val response = api.execute(AnilistQueries.VIEWER) ?: return null
        val viewer = response["data"]?.jsonObject?.get("Viewer") ?: return null
        if (viewer == JsonNull) return null

        val v = viewer.jsonObject
        val stats = v["statistics"]!!.jsonObject
        val user = User(
            id = v["id"]!!.jsonPrimitive.content.toInt(),
            name = v["name"]!!.jsonPrimitive.content,
            avatar = v["avatar"]?.jsonObject?.get("medium")?.jsonPrimitive?.content,
            episodesWatched = stats["anime"]!!.jsonObject["episodesWatched"]!!.jsonPrimitive.content.toInt(),
            chaptersRead = stats["manga"]!!.jsonObject["chaptersRead"]!!.jsonPrimitive.content.toInt(),
            displayAdultContent = v["options"]?.jsonObject?.get("displayAdultContent")?.jsonPrimitive?.content == "true",
        )
        tokenStorage.saveUserId(user.id)
        cachedUser = user
        return user
    }

    // ── Single Media ──────────────────────────────────────────

    override suspend fun getMedia(id: Int): Media? {
        val response = api.execute(AnilistQueries.media(id)) ?: return null
        val media = response["data"]?.jsonObject?.get("Media") ?: return null
        if (media == JsonNull) return null
        return MediaParser.parseMedia(media.jsonObject)
    }

    // ── Discovery ─────────────────────────────────────────────

    override suspend fun getTrendingAnime(page: Int): List<Media> =
        fetchPagedMedia(AnilistQueries.trending("ANIME", page))

    override suspend fun getPopularAnime(page: Int): List<Media> =
        fetchPagedMedia(AnilistQueries.popular("ANIME", page))

    override suspend fun getRecentlyUpdatedAnime(page: Int): List<Media> =
        fetchPagedMedia(AnilistQueries.recentlyUpdated("ANIME", page))

    override suspend fun getTrendingManga(page: Int): List<Media> =
        fetchPagedMedia(AnilistQueries.trending("MANGA", page))

    override suspend fun getPopularManga(page: Int): List<Media> =
        fetchPagedMedia(AnilistQueries.popular("MANGA", page))

    override suspend fun getTrendingNovels(page: Int): List<Media> =
        fetchPagedMedia(AnilistQueries.trendingNovels(page))

    // ── User Lists ────────────────────────────────────────────

    override suspend fun getUserAnimeList(status: String): List<Media> =
        fetchContinueMedia("ANIME", status)

    override suspend fun getUserMangaList(status: String): List<Media> =
        fetchContinueMedia("MANGA", status)

    override suspend fun getRecommendations(): List<Media> {
        val response = api.execute(AnilistQueries.recommendations()) ?: return emptyList()
        val recs = response["data"]?.jsonObject
            ?.get("Page")?.jsonObject
            ?.get("recommendations")?.jsonArray ?: return emptyList()

        val seen = mutableSetOf<Int>()
        return recs.reversed().mapNotNull { rec ->
            val json = rec.jsonObject["mediaRecommendation"]
            if (json == null || json == JsonNull) return@mapNotNull null
            val media = MediaParser.parseMedia(json.jsonObject)
            if (media.id in seen) null else { seen.add(media.id); media }
        }
    }

    // ── Search ────────────────────────────────────────────────

    override suspend fun search(
        query: String,
        type: String,
        genres: List<String>?,
        sort: String?,
        page: Int,
    ): List<Media> {
        val genreStr = genres?.joinToString(",") { "\"$it\"" }
        return fetchPagedMedia(AnilistQueries.search(query, type, page, genres = genreStr, sort = sort))
    }

    // ── Mutations ─────────────────────────────────────────────

    override suspend fun toggleFavorite(id: Int, isAnime: Boolean) {
        val (query, variables) = AnilistQueries.toggleFav(isAnime, id)
        api.execute(query, variables)
    }

    override suspend fun editListEntry(mediaId: Int, progress: Int?, score: Int?, status: String?) {
        val (query, variables) = AnilistQueries.editList(mediaId, progress, score, status)
        api.execute(query, variables)
    }

    override suspend fun deleteListEntry(listId: Int) {
        val (query, variables) = AnilistQueries.deleteList(listId)
        api.execute(query, variables)
    }

    // ── Helpers ────────────────────────────────────────────────

    private suspend fun fetchPagedMedia(query: String): List<Media> {
        val response = api.execute(query) ?: return emptyList()
        val mediaArray = response["data"]?.jsonObject
            ?.get("Page")?.jsonObject
            ?.get("media")?.jsonArray ?: return emptyList()
        return mediaArray.mapNotNull { entry ->
            try {
                MediaParser.parseMedia(entry.jsonObject)
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun fetchContinueMedia(type: String, status: String): List<Media> {
        val userId = tokenStorage.getUserId()
        if (userId == -1) return emptyList()
        val response = api.execute(AnilistQueries.continueMedia(userId, type, status)) ?: return emptyList()
        val lists = response["data"]?.jsonObject
            ?.get("MediaListCollection")?.takeIf { it != JsonNull }
            ?.jsonObject?.get("lists")?.jsonArray ?: return emptyList()

        if (lists.isEmpty()) return emptyList()
        return lists[0].jsonObject["entries"]!!.jsonArray.reversed().mapNotNull { entry ->
            try {
                MediaParser.parseMediaFromListEntry(entry.jsonObject, type)
            } catch (e: Exception) {
                null
            }
        }
    }
}
