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

data class AnimeSource(
    val slug: String,
    val name: String,
    val cover: String? = null,
)
