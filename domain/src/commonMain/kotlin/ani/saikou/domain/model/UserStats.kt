package ani.saikou.domain.model

data class UserStats(
    val userName: String,
    val avatar: String? = null,
    val anime: AnimeStats = AnimeStats(),
    val manga: MangaStats = MangaStats(),
)

data class AnimeStats(
    val count: Int = 0,
    val episodesWatched: Int = 0,
    val minutesWatched: Int = 0,
    val meanScore: Float = 0f,
    val genres: List<GenreStat> = emptyList(),
    val statuses: List<StatusStat> = emptyList(),
    val scores: List<ScoreStat> = emptyList(),
)

data class MangaStats(
    val count: Int = 0,
    val chaptersRead: Int = 0,
    val volumesRead: Int = 0,
    val meanScore: Float = 0f,
    val genres: List<GenreStat> = emptyList(),
    val statuses: List<StatusStat> = emptyList(),
)

data class GenreStat(
    val genre: String,
    val count: Int,
    val meanScore: Float = 0f,
    val minutesWatched: Int = 0,
    val chaptersRead: Int = 0,
)

data class StatusStat(
    val status: String,
    val count: Int,
)

data class ScoreStat(
    val score: Int,
    val count: Int,
)
