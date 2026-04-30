package ani.saikou.data.repository

import ani.saikou.data.local.TokenStorage
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.remote.AnilistQueries
import ani.saikou.data.remote.MediaParser
import ani.saikou.domain.model.AiringEntry
import ani.saikou.domain.model.AnimeStats
import ani.saikou.domain.model.CharacterDetail
import ani.saikou.domain.model.GenreStat
import ani.saikou.domain.model.MangaStats
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.ScoreStat
import ani.saikou.domain.model.StatusStat
import ani.saikou.domain.model.User
import ani.saikou.domain.model.UserStats
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

    // ── Stats ─────────────────────────────────────────────────

    override suspend fun getUserStats(): UserStats? {
        val response = api.execute(AnilistQueries.USER_STATS) ?: return null
        val viewer = response["data"]?.jsonObject?.get("Viewer") ?: return null
        if (viewer == JsonNull) return null

        val v = viewer.jsonObject
        val stats = v["statistics"]?.jsonObject ?: return null

        val animeStats = stats["anime"]?.jsonObject
        val mangaStats = stats["manga"]?.jsonObject

        return UserStats(
            userName = v["name"]?.jsonPrimitive?.content ?: "User",
            avatar =
                v["avatar"]
                    ?.jsonObject
                    ?.get("medium")
                    ?.jsonPrimitive
                    ?.content,
            anime =
                animeStats?.let { a ->
                    AnimeStats(
                        count = a["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        episodesWatched = a["episodesWatched"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        minutesWatched = a["minutesWatched"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        meanScore = a["meanScore"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                        genres =
                            a["genres"]?.jsonArray?.mapNotNull { g ->
                                val obj = g.jsonObject
                                val genre = obj["genre"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                GenreStat(
                                    genre = genre,
                                    count = obj["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                    meanScore = obj["meanScore"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                                    minutesWatched = obj["minutesWatched"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                )
                            } ?: emptyList(),
                        statuses =
                            a["statuses"]?.jsonArray?.mapNotNull { s ->
                                val obj = s.jsonObject
                                val status = obj["status"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                StatusStat(status = status, count = obj["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                            } ?: emptyList(),
                        scores =
                            a["scores"]
                                ?.jsonArray
                                ?.mapNotNull { s ->
                                    val obj = s.jsonObject
                                    ScoreStat(
                                        score = obj["score"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null,
                                        count = obj["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                    )
                                }?.filter { it.count > 0 } ?: emptyList(),
                    )
                } ?: AnimeStats(),
            manga =
                mangaStats?.let { m ->
                    MangaStats(
                        count = m["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        chaptersRead = m["chaptersRead"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        volumesRead = m["volumesRead"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        meanScore = m["meanScore"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                        genres =
                            m["genres"]?.jsonArray?.mapNotNull { g ->
                                val obj = g.jsonObject
                                val genre = obj["genre"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                GenreStat(
                                    genre = genre,
                                    count = obj["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                    meanScore = obj["meanScore"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                                    chaptersRead = obj["chaptersRead"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                )
                            } ?: emptyList(),
                        statuses =
                            m["statuses"]?.jsonArray?.mapNotNull { s ->
                                val obj = s.jsonObject
                                val status = obj["status"]?.jsonPrimitive?.content ?: return@mapNotNull null
                                StatusStat(status = status, count = obj["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                            } ?: emptyList(),
                    )
                } ?: MangaStats(),
        )
    }

    // ── Auth ──────────────────────────────────────────────────

    override fun getToken(): String? = tokenStorage.getToken()

    override fun saveToken(token: String) = tokenStorage.saveToken(token)

    override fun isLoggedIn(): Boolean = tokenStorage.isLoggedIn()

    // ── User ──────────────────────────────────────────────────

    override suspend fun getUserData(): User? {
        // Always fetch fresh data — stats change after watching episodes
        val response = api.execute(AnilistQueries.VIEWER) ?: return cachedUser
        val viewer = response["data"]?.jsonObject?.get("Viewer") ?: return null
        if (viewer == JsonNull) return null

        val v = viewer.jsonObject
        val stats = v["statistics"]!!.jsonObject
        val user =
            User(
                id = v["id"]!!.jsonPrimitive.content.toInt(),
                name = v["name"]!!.jsonPrimitive.content,
                avatar =
                    v["avatar"]
                        ?.jsonObject
                        ?.get("medium")
                        ?.jsonPrimitive
                        ?.content,
                episodesWatched =
                    stats["anime"]!!
                        .jsonObject["episodesWatched"]!!
                        .jsonPrimitive.content
                        .toInt(),
                chaptersRead =
                    stats["manga"]!!
                        .jsonObject["chaptersRead"]!!
                        .jsonPrimitive.content
                        .toInt(),
                displayAdultContent =
                    v["options"]
                        ?.jsonObject
                        ?.get("displayAdultContent")
                        ?.jsonPrimitive
                        ?.content == "true",
            )
        tokenStorage.saveUserId(user.id)
        cachedUser = user
        return user
    }

    // ── Single Media ──────────────────────────────────────────

    override suspend fun getMedia(id: Int): Media? {
        val response = api.execute(AnilistQueries.mediaDetails(id)) ?: return null
        val media = response["data"]?.jsonObject?.get("Media") ?: return null
        if (media == JsonNull) return null
        return MediaParser.parseMediaDetail(media.jsonObject)
    }

    override suspend fun getCharacter(id: Int): CharacterDetail? {
        val response = api.execute(AnilistQueries.character(id)) ?: return null
        val c = response["data"]?.jsonObject?.get("Character") ?: return null
        if (c == JsonNull) return null
        val json = c.jsonObject
        val name = json["name"]?.jsonObject
        val image = json["image"]?.jsonObject

        val mediaEdges = json["media"]?.jsonObject?.get("edges")?.jsonArray
        val mediaList =
            mediaEdges?.mapNotNull { edge ->
                try {
                    val node = edge.jsonObject["node"]!!.jsonObject
                    MediaParser.parseMedia(node)
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

        return CharacterDetail(
            id = json["id"]!!.jsonPrimitive.content.toInt(),
            name = name?.get("userPreferred")?.jsonPrimitive?.content,
            nativeName = name?.get("native")?.jsonPrimitive?.content,
            image = image?.get("large")?.jsonPrimitive?.content,
            description = json["description"]?.jsonPrimitive?.content,
            gender = json["gender"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content,
            age = json["age"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content,
            favourites = json["favourites"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            media = mediaList,
        )
    }

    // ── Discovery ─────────────────────────────────────────────

    override suspend fun getTrendingAnime(page: Int): List<Media> = fetchPagedMedia(AnilistQueries.trending("ANIME", page))

    override suspend fun getPopularAnime(page: Int): List<Media> = fetchPagedMedia(AnilistQueries.popular("ANIME", page))

    override suspend fun getRecentlyUpdatedAnime(page: Int): List<Media> = fetchPagedMedia(AnilistQueries.recentlyUpdated("ANIME", page))

    override suspend fun getTrendingManga(page: Int): List<Media> = fetchPagedMedia(AnilistQueries.trending("MANGA", page))

    override suspend fun getPopularManga(page: Int): List<Media> = fetchPagedMedia(AnilistQueries.popular("MANGA", page))

    override suspend fun getRecentlyUpdatedManga(page: Int): List<Media> = fetchPagedMedia(AnilistQueries.recentlyUpdated("MANGA", page))

    override suspend fun getTrendingNovels(page: Int): List<Media> = fetchPagedMedia(AnilistQueries.trendingNovels(page))

    // ── User Lists ────────────────────────────────────────────

    override suspend fun getUserAnimeList(status: String): List<Media> = fetchContinueMedia("ANIME", status)

    override suspend fun getUserMangaList(status: String): List<Media> = fetchContinueMedia("MANGA", status)

    override suspend fun getUserFavorites(type: String): List<Media> {
        val userId = tokenStorage.getUserId()
        if (userId == -1) return emptyList()
        val response = api.execute(AnilistQueries.userFavorites(userId, type)) ?: return emptyList()
        val field = if (type == "ANIME") "anime" else "manga"
        val nodes =
            response["data"]
                ?.jsonObject
                ?.get("User")
                ?.takeIf { it != JsonNull }
                ?.jsonObject
                ?.get("favourites")
                ?.takeIf { it != JsonNull }
                ?.jsonObject
                ?.get(field)
                ?.takeIf { it != JsonNull }
                ?.jsonObject
                ?.get("nodes")
                ?.jsonArray ?: return emptyList()
        return nodes.mapNotNull { node ->
            try {
                MediaParser.parseMedia(node.jsonObject)
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun getRecommendations(): List<Media> {
        val response = api.execute(AnilistQueries.recommendations()) ?: return emptyList()
        val recs =
            response["data"]
                ?.jsonObject
                ?.get("Page")
                ?.jsonObject
                ?.get("recommendations")
                ?.jsonArray ?: return emptyList()

        val seen = mutableSetOf<Int>()
        return recs.reversed().mapNotNull { rec ->
            val json = rec.jsonObject["mediaRecommendation"]
            if (json == null || json == JsonNull) return@mapNotNull null
            val media = MediaParser.parseMedia(json.jsonObject)
            if (media.id in seen) {
                null
            } else {
                seen.add(media.id)
                media
            }
        }
    }

    // ── Seasonal ──────────────────────────────────────────────

    override suspend fun getSeasonalAnime(
        season: String,
        year: Int,
        page: Int,
    ): List<Media> = fetchPagedMedia(AnilistQueries.seasonal(season, year, page))

    override suspend fun getAiringSchedule(
        weekStart: Long,
        weekEnd: Long,
        page: Int,
    ): List<AiringEntry> {
        // AniList caps at 50 per page — fetch multiple pages to get the full week
        val all = mutableListOf<AiringEntry>()
        val seen = mutableSetOf<Int>()
        var currentPage = 1
        val maxPages = 4 // safety cap

        while (currentPage <= maxPages) {
            val response =
                api.execute(AnilistQueries.airingSchedule(weekStart, weekEnd, currentPage))
                    ?: break
            val schedules =
                response["data"]
                    ?.jsonObject
                    ?.get("Page")
                    ?.jsonObject
                    ?.get("airingSchedules")
                    ?.jsonArray ?: break

            if (schedules.isEmpty()) break

            for (entry in schedules) {
                try {
                    val obj = entry.jsonObject
                    val airingAt = obj["airingAt"]!!.jsonPrimitive.content.toLong()
                    val episode = obj["episode"]!!.jsonPrimitive.content.toInt()
                    val mediaJson = obj["media"]?.takeIf { it != JsonNull }?.jsonObject ?: continue
                    val media = MediaParser.parseMedia(mediaJson)
                    if (media.isAdult || media.id in seen) continue
                    seen.add(media.id)
                    all.add(AiringEntry(airingAt = airingAt, episode = episode, media = media))
                } catch (_: Exception) {
                    // skip
                }
            }

            // If we got fewer than 50, there are no more pages
            if (schedules.size < 50) break
            currentPage++
        }
        return all
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

    override suspend fun toggleFavorite(
        id: Int,
        isAnime: Boolean,
    ) {
        val (query, variables) = AnilistQueries.toggleFav(isAnime, id)
        api.execute(query, variables)
    }

    override suspend fun editListEntry(
        mediaId: Int,
        progress: Int?,
        score: Int?,
        status: String?,
    ) {
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
        val mediaArray =
            response["data"]
                ?.jsonObject
                ?.get("Page")
                ?.jsonObject
                ?.get("media")
                ?.jsonArray ?: return emptyList()
        return mediaArray.mapNotNull { entry ->
            try {
                MediaParser.parseMedia(entry.jsonObject)
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun fetchContinueMedia(
        type: String,
        status: String,
    ): List<Media> {
        val userId = tokenStorage.getUserId()
        if (userId == -1) return emptyList()
        val response = api.execute(AnilistQueries.continueMedia(userId, type, status)) ?: return emptyList()
        val lists =
            response["data"]
                ?.jsonObject
                ?.get("MediaListCollection")
                ?.takeIf { it != JsonNull }
                ?.jsonObject
                ?.get("lists")
                ?.jsonArray ?: return emptyList()

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
