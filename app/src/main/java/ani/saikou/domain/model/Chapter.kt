package ani.saikou.domain.model

data class Chapter(
    val id: String,
    val number: Float,
    val name: String,
)

data class MangaPage(
    val index: Int,
    val imageUrl: String,
)

data class MangaSource(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
)
