package ani.saikou.domain.model

data class Media(
    val id: Int,
    val malId: Int? = null,
    val name: String?,
    val nameRomaji: String?,
    val userPreferredName: String? = null,
    val cover: String? = null,
    val banner: String? = null,
    val status: String? = null,
    val format: String? = null,
    val type: String? = null, // ANIME or MANGA
    val meanScore: Int? = null,
    val isAdult: Boolean = false,
    val isFav: Boolean = false,
    val userProgress: Int? = null,
    val userScore: Int = 0,
    val userStatus: String? = null,
    val userListEntryId: Int? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val totalEpisodes: Int? = null,
    val totalChapters: Int? = null,
    val nextAiringEpisode: Int? = null,
    val nextAiringEpisodeTime: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val episodeDuration: Int? = null,
    val mainStudio: String? = null,
    val characters: List<Character>? = null,
    val relations: List<Media>? = null,
    val recommendations: List<Media>? = null,
) {

    val displayTitle: String
        get() = userPreferredName ?: name ?: nameRomaji ?: "Unknown"

    val episodeProgress: String?
        get() {
            if (userProgress == null) return null
            val total = totalEpisodes ?: totalChapters
            // Don't render "12 / ?" when AniList has no count — happens for
            // licensed/on-hiatus manga (Vagabond, Berserk) or anime in airing
            // mid-season. Just show the progress on its own.
            return if (total != null && total > 0) "$userProgress / $total" else "$userProgress"
        }

    val isOngoing: Boolean
        get() = status == "RELEASING"
}

data class Character(
    val id: Int,
    val name: String?,
    val image: String? = null,
    val role: String? = null,
)

data class CharacterDetail(
    val id: Int,
    val name: String?,
    val nativeName: String? = null,
    val image: String? = null,
    val description: String? = null,
    val gender: String? = null,
    val age: String? = null,
    val favourites: Int = 0,
    val media: List<Media> = emptyList(),
)

data class Studio(
    val id: Int,
    val name: String?,
)
