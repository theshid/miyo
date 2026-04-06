package ani.saikou.data.remote

/**
 * All AniList GraphQL query strings, extracted for readability.
 */
object AnilistQueries {

    const val VIEWER = """{Viewer{name options{displayAdultContent}avatar{medium}id statistics{anime{episodesWatched}manga{chaptersRead}}}}"""

    fun media(id: Int) =
        """{Media(id:$id){id status chapters episodes nextAiringEpisode{episode}type meanScore isAdult isFavourite bannerImage coverImage{large}title{english romaji userPreferred}mediaListEntry{progress score(format:POINT_100)status}}}"""

    fun mediaDetails(id: Int) =
        """{Media(id:$id){mediaListEntry{id status score(format:POINT_100)progress repeat updatedAt startedAt{year month day}completedAt{year month day}}isFavourite siteUrl idMal nextAiringEpisode{episode airingAt}source countryOfOrigin format duration season seasonYear startDate{year month day}endDate{year month day}genres studios(isMain:true){nodes{id name siteUrl}}description characters(sort:[ROLE,FAVOURITES_DESC],perPage:25,page:1){edges{role node{id image{medium}name{userPreferred}}}}relations{edges{relationType(version:2)node{id mediaListEntry{progress score(format:POINT_100)status}episodes chapters nextAiringEpisode{episode}meanScore isAdult isFavourite title{english romaji userPreferred}type status(version:2)bannerImage coverImage{large}}}}recommendations{nodes{mediaRecommendation{id mediaListEntry{progress score(format:POINT_100)status}episodes chapters nextAiringEpisode{episode}meanScore isAdult isFavourite title{english romaji userPreferred}type status(version:2)bannerImage coverImage{large}}}}externalLinks{url site}}}"""

    fun continueMedia(userId: Int, type: String, status: String) =
        """{ MediaListCollection(userId: $userId, type: $type, status: $status, sort: UPDATED_TIME) { lists { entries { progress score(format:POINT_100) status media { id isAdult status chapters episodes nextAiringEpisode{episode} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} } } } } }"""

    fun recommendations() =
        """{ Page(page:1,perPage:30) { recommendations(sort:RATING_DESC,onList:true) { mediaRecommendation { id isAdult mediaListEntry{progress score(format:POINT_100)status} chapters isFavourite episodes nextAiringEpisode{episode} meanScore title{english romaji userPreferred} type status(version:2) bannerImage coverImage{large} } } } }"""

    fun trending(type: String, page: Int = 1, perPage: Int = 10) =
        """{ Page(page:$page,perPage:$perPage) { media(type:$type,sort:TRENDING_DESC) { id isAdult status chapters episodes nextAiringEpisode{episode} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""

    fun popular(type: String, page: Int = 1, perPage: Int = 10) =
        """{ Page(page:$page,perPage:$perPage) { media(type:$type,sort:POPULARITY_DESC) { id isAdult status chapters episodes nextAiringEpisode{episode} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""

    fun recentlyUpdated(type: String, page: Int = 1, perPage: Int = 10) =
        """{ Page(page:$page,perPage:$perPage) { media(type:$type,sort:UPDATED_AT_DESC,status:RELEASING) { id isAdult status chapters episodes nextAiringEpisode{episode} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""

    fun trendingNovels(page: Int = 1, perPage: Int = 10) =
        """{ Page(page:$page,perPage:$perPage) { media(type:MANGA,format:NOVEL,sort:TRENDING_DESC) { id isAdult status chapters meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""

    fun search(query: String, type: String, page: Int = 1, perPage: Int = 20, genres: String? = null, sort: String? = null) =
        """{ Page(page:$page,perPage:$perPage) { media(type:$type,search:"$query"${if (sort != null) ",sort:$sort" else ""}${if (genres != null) ",genre_in:[$genres]" else ""}) { id isAdult status chapters episodes nextAiringEpisode{episode} meanScore isFavourite bannerImage coverImage{large} title{english romaji userPreferred} mediaListEntry{progress score(format:POINT_100)status} } } }"""

    fun mediaLists(userId: Int, type: String) =
        """{ MediaListCollection(userId:$userId,type:$type) { lists { name entries { status progress score(format:POINT_100) media { id isAdult status chapters episodes nextAiringEpisode{episode} bannerImage meanScore isFavourite coverImage{large} title{english romaji userPreferred} } } } user { mediaListOptions { rowOrder animeList{sectionOrder} mangaList{sectionOrder} } } } }"""

    fun toggleFav(isAnime: Boolean, id: Int): Pair<String, String> {
        val query = """mutation(${"$"}animeId:Int,${"$"}mangaId:Int){ToggleFavourite(animeId:${"$"}animeId,mangaId:${"$"}mangaId){anime{edges{id}}manga{edges{id}}}}"""
        val variables = if (isAnime) """{\"animeId\":\"$id\"}""" else """{\"mangaId\":\"$id\"}"""
        return query to variables
    }

    fun editList(mediaId: Int, progress: Int?, score: Int?, status: String?): Pair<String, String> {
        val query = """mutation(${"$"}mediaID:Int,${"$"}progress:Int,${"$"}scoreRaw:Int,${"$"}status:MediaListStatus){SaveMediaListEntry(mediaId:${"$"}mediaID,progress:${"$"}progress,scoreRaw:${"$"}scoreRaw,status:${"$"}status){score(format:POINT_10_DECIMAL)}}"""
        val variables = buildString {
            append("""{\"mediaID\":\"$mediaId\"""")
            if (progress != null) append(""",\"progress\":\"$progress\"""")
            if (score != null) append(""",\"scoreRaw\":\"$score\"""")
            if (status != null) append(""",\"status\":\"$status\"""")
            append("}")
        }
        return query to variables
    }

    fun deleteList(listId: Int): Pair<String, String> {
        val query = """mutation(${"$"}id:Int){DeleteMediaListEntry(id:${"$"}id){deleted}}"""
        val variables = """{\"id\":\"$listId\"}"""
        return query to variables
    }
}
