@file:Suppress("ktlint:standard:max-line-length")
// GraphQL queries can't be wrapped without breaking tokens — single-line
// per-query is the readable form even past ktlint's line-length cap.

package ani.saikou.data.remote

/**
 * All AniList GraphQL query strings, extracted for readability.
 */
object AnilistQueries {
    const val VIEWER = """{Viewer{name options{displayAdultContent}avatar{medium}id statistics{anime{episodesWatched}manga{chaptersRead}}}}"""

    const val USER_STATS = """{Viewer{name avatar{medium}statistics{anime{count meanScore minutesWatched episodesWatched genres(sort:COUNT_DESC,limit:10){genre count meanScore minutesWatched}statuses{status count}scores{score count}}manga{count meanScore chaptersRead volumesRead genres(sort:COUNT_DESC,limit:10){genre count meanScore chaptersRead}statuses{status count}}}}}"""

    fun media(id: Int) =
        """{Media(id:$id){id idMal status chapters episodes nextAiringEpisode{episode}type meanScore isAdult isFavourite bannerImage coverImage{large}title{english romaji userPreferred}mediaListEntry{progress score(format:POINT_100)status}}}"""

    fun mediaDetails(id: Int) =
        """{Media(id:$id){id idMal type status isAdult meanScore isFavourite episodes chapters bannerImage coverImage{large}title{english romaji userPreferred}nextAiringEpisode{episode airingAt}mediaListEntry{id status score(format:POINT_100)progress}format duration season seasonYear startDate{year month day}endDate{year month day}genres studios(isMain:true){nodes{id name}}description characters(sort:[ROLE,FAVOURITES_DESC],perPage:25,page:1){edges{role node{id image{medium}name{userPreferred}}}}relations{edges{relationType(version:2)node{id mediaListEntry{progress score(format:POINT_100)status}episodes chapters nextAiringEpisode{episode}meanScore isAdult isFavourite title{english romaji userPreferred}type status(version:2)bannerImage coverImage{large}}}}recommendations{nodes{mediaRecommendation{id mediaListEntry{progress score(format:POINT_100)status}episodes chapters nextAiringEpisode{episode}meanScore isAdult isFavourite title{english romaji userPreferred}type status(version:2)bannerImage coverImage{large}}}}}}"""

    fun continueMedia(
        userId: Int,
        type: String,
        status: String,
    ) =
        """{ MediaListCollection(userId: $userId, type: $type, status: $status, sort: UPDATED_TIME) { lists { entries { progress score(format:POINT_100) status media { id isAdult status chapters episodes nextAiringEpisode{episode airingAt} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} } } } } }"""

    const val RECOMMENDATIONS =
        """{ Page(page:1,perPage:30) { recommendations(sort:RATING_DESC,onList:true) { mediaRecommendation { id isAdult mediaListEntry{progress score(format:POINT_100)status} chapters isFavourite episodes nextAiringEpisode{episode} meanScore title{english romaji userPreferred} type status(version:2) bannerImage coverImage{large} } } } }"""

    fun trending(
        type: String,
        page: Int = 1,
        perPage: Int = 10,
    ) =
        """{ Page(page:$page,perPage:$perPage) { media(type:$type,sort:TRENDING_DESC) { id isAdult status chapters episodes nextAiringEpisode{episode} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""

    fun popular(
        type: String,
        page: Int = 1,
        perPage: Int = 10,
    ) =
        """{ Page(page:$page,perPage:$perPage) { media(type:$type,sort:POPULARITY_DESC) { id isAdult status chapters episodes nextAiringEpisode{episode} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""

    fun recentlyUpdated(
        type: String,
        page: Int = 1,
        perPage: Int = 10,
    ) =
        """{ Page(page:$page,perPage:$perPage) { media(type:$type,sort:UPDATED_AT_DESC,status:RELEASING) { id isAdult status chapters episodes nextAiringEpisode{episode} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""

    fun trendingNovels(
        page: Int = 1,
        perPage: Int = 10,
    ) =
        """{ Page(page:$page,perPage:$perPage) { media(type:MANGA,format:NOVEL,sort:TRENDING_DESC) { id isAdult status chapters meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""

    fun seasonal(
        season: String,
        year: Int,
        page: Int = 1,
        perPage: Int = 25,
    ) =
        """{ Page(page:$page,perPage:$perPage) { media(type:ANIME,season:$season,seasonYear:$year,sort:POPULARITY_DESC) { id isAdult status episodes nextAiringEpisode{episode airingAt} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} format studios(isMain:true){nodes{name}} } } }"""

    fun airingSchedule(
        weekStart: Long,
        weekEnd: Long,
        page: Int = 1,
        perPage: Int = 100,
    ) =
        """{ Page(page:$page,perPage:$perPage) { airingSchedules(airingAt_greater:$weekStart,airingAt_lesser:$weekEnd,sort:TIME) { airingAt episode media { id isAdult status episodes nextAiringEpisode{episode airingAt} meanScore isFavourite coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } } }"""

    fun search(
        query: String,
        type: String,
        page: Int = 1,
        perPage: Int = 20,
        genres: String? = null,
        sort: String? = null,
    ): String {
        // Omit `search:""` when no query — AniList treats empty string as
        // "match nothing", but if we drop the field the genre/sort filters
        // become a pure browse (e.g. "all Action anime by popularity").
        val searchClause = if (query.isNotBlank()) ",search:\"$query\"" else ""
        val sortClause = if (sort != null) ",sort:$sort" else ""
        val genresClause = if (genres != null) ",genre_in:[$genres]" else ""
        // Default sort when browsing without a query so users see something
        // sensible (popularity) instead of AniList's id-asc default.
        val effectiveSort = if (query.isBlank() && sort == null) ",sort:POPULARITY_DESC" else sortClause
        return """{ Page(page:$page,perPage:$perPage) { media(type:$type$searchClause$effectiveSort$genresClause) { id isAdult status chapters episodes nextAiringEpisode{episode} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""
    }

    fun mediaLists(
        userId: Int,
        type: String,
    ) =
        """{ MediaListCollection(userId:$userId,type:$type) { lists { name entries { status progress score(format:POINT_100) media { id isAdult status chapters episodes nextAiringEpisode{episode} bannerImage meanScore isFavourite coverImage{large} title{english romaji userPreferred} } } } user { mediaListOptions { rowOrder animeList{sectionOrder} mangaList{sectionOrder} } } } }"""

    fun character(id: Int) =
        """{Character(id:$id){id name{full native userPreferred}image{large medium}description gender dateOfBirth{year month day}age bloodType favourites media(sort:POPULARITY_DESC){edges{node{id type isAdult status meanScore isFavourite bannerImage coverImage{large}title{english romaji userPreferred}mediaListEntry{progress score(format:POINT_100)status}}characterRole}}}}"""

    fun userFavorites(
        userId: Int,
        type: String,
        page: Int = 1,
        perPage: Int = 50,
    ): String {
        val field = if (type == "ANIME") "anime" else "manga"
        return """{User(id:$userId){favourites{$field(page:$page,perPage:$perPage){nodes{id isAdult status chapters episodes nextAiringEpisode{episode} type meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100) status}}}}}}"""
    }

    fun toggleFav(
        isAnime: Boolean,
        id: Int,
    ): Pair<String, String> {
        val query = """mutation(${"$"}animeId:Int,${"$"}mangaId:Int){ToggleFavourite(animeId:${"$"}animeId,mangaId:${"$"}mangaId){anime{edges{id}}manga{edges{id}}}}"""
        val variables = if (isAnime) """{"animeId":$id}""" else """{"mangaId":$id}"""
        return query to variables
    }

    fun editList(
        mediaId: Int,
        progress: Int?,
        score: Int?,
        status: String?,
    ): Pair<String, String> {
        val query = """mutation(${"$"}mediaID:Int,${"$"}progress:Int,${"$"}scoreRaw:Int,${"$"}status:MediaListStatus){SaveMediaListEntry(mediaId:${"$"}mediaID,progress:${"$"}progress,scoreRaw:${"$"}scoreRaw,status:${"$"}status){score(format:POINT_10_DECIMAL)}}"""
        val variables =
            buildString {
                append("""{"mediaID":$mediaId""")
                if (progress != null) append(""","progress":$progress""")
                if (score != null) append(""","scoreRaw":$score""")
                if (status != null) append(",\"status\":\"$status\"")
                append("}")
            }
        return query to variables
    }

    fun deleteList(listId: Int): Pair<String, String> {
        val query = """mutation(${"$"}id:Int){DeleteMediaListEntry(id:${"$"}id){deleted}}"""
        val variables = """{"id":$listId}"""
        return query to variables
    }
}
