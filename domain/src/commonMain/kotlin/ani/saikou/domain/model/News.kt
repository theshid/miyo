package ani.saikou.domain.model

data class NewsItem(
    val title: String,
    val description: String = "",
    val url: String,
    val imageUrl: String? = null,
    val source: String,
    val date: Long, // unix millis
    val category: NewsCategory,
)

enum class NewsCategory {
    EPISODE_RELEASE,
    CHAPTER_RELEASE,
    INDUSTRY_NEWS,
    DISCUSSION,
    SCHEDULE,
    ANNOUNCEMENT,
}

data class AiringScheduleItem(
    val mediaId: Int,
    val title: String,
    val imageUrl: String?,
    val airingTime: String,
    val episode: Int?,
)
