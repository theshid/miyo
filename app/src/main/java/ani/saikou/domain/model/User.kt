package ani.saikou.domain.model

data class User(
    val id: Int,
    val name: String,
    val avatar: String? = null,
    val episodesWatched: Int = 0,
    val chaptersRead: Int = 0,
    val displayAdultContent: Boolean = false,
)
