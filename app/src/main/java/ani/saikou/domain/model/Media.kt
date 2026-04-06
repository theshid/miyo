package ani.saikou.domain.model

import java.io.Serializable

data class Media(
    val id: Int,
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
) : Serializable {

    val displayTitle: String
        get() = userPreferredName ?: name ?: nameRomaji ?: "Unknown"

    val episodeProgress: String?
        get() {
            if (userProgress == null) return null
            val total = totalEpisodes ?: totalChapters ?: "?"
            return "$userProgress / $total"
        }

    val isOngoing: Boolean
        get() = status == "RELEASING"
}

data class Character(
    val id: Int,
    val name: String?,
    val image: String? = null,
    val role: String? = null,
) : Serializable

data class Studio(
    val id: Int,
    val name: String?,
) : Serializable
