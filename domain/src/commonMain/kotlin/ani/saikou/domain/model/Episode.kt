package ani.saikou.domain.model

data class Episode(
    val number: String,
    val link: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val isFiller: Boolean = false,
)

data class StreamLink(
    val server: String,
    val url: String,
    val quality: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
)

data class SubtitleTrack(
    val url: String,
    val label: String = "English",
    val language: String = "en",
)

/**
 * One hit from an [ani.saikou.domain.source.AnimeSource.search] call —
 * mirrors [MangaSearchResult] for the manga side. Source-specific id
 * formats are treated as opaque by callers (Gogo returns a URL slug;
 * future sources will return their own id shape).
 */
data class AnimeSearchResult(
    val slug: String,
    val name: String,
    val cover: String? = null,
)
