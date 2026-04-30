package ani.saikou.domain.model

data class AiringEntry(
    val airingAt: Long, // epoch seconds
    val episode: Int,
    val media: Media,
)
