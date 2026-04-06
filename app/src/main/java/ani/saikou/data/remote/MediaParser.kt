package ani.saikou.data.remote

import ani.saikou.domain.model.Character
import ani.saikou.domain.model.Media
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses AniList JSON responses into domain models.
 * Centralizes all the JSON → Media/Character conversion logic.
 */
object MediaParser {

    fun parseMedia(json: JsonObject): Media {
        val title = json["title"]!!.jsonObject
        val listEntry = json["mediaListEntry"]
        val hasEntry = listEntry != null && listEntry != JsonNull
        val type = json.str("type")

        return Media(
            id = json.int("id"),
            name = title.str("english"),
            nameRomaji = title.str("romaji"),
            userPreferredName = title.str("userPreferred"),
            cover = json["coverImage"]?.jsonObject?.str("large"),
            banner = json.strOrNull("bannerImage"),
            status = json.str("status"),
            type = type,
            meanScore = json.intOrNull("meanScore"),
            isAdult = json.str("isAdult") == "true",
            isFav = json.str("isFavourite") == "true",
            userProgress = if (hasEntry) listEntry!!.jsonObject.int("progress") else null,
            userScore = if (hasEntry) listEntry!!.jsonObject.int("score") else 0,
            userStatus = if (hasEntry) listEntry!!.jsonObject.str("status") else null,
            totalEpisodes = if (type == "ANIME") json.intOrNull("episodes") else null,
            totalChapters = if (type == "MANGA") json.intOrNull("chapters") else null,
            nextAiringEpisode = json["nextAiringEpisode"]?.takeIf { it != JsonNull }
                ?.jsonObject?.int("episode")?.let { it - 1 },
        )
    }

    fun parseMediaFromListEntry(json: JsonObject, type: String): Media {
        val media = json["media"]!!.jsonObject
        val title = media["title"]!!.jsonObject

        return Media(
            id = media.int("id"),
            name = title.str("english"),
            nameRomaji = title.str("romaji"),
            userPreferredName = title.str("userPreferred"),
            cover = media["coverImage"]?.jsonObject?.str("large"),
            banner = media.strOrNull("bannerImage"),
            status = media.str("status"),
            type = type,
            meanScore = media.intOrNull("meanScore"),
            isAdult = media.str("isAdult") == "true",
            isFav = media.str("isFavourite") == "true",
            userProgress = json.int("progress"),
            userScore = json.int("score"),
            userStatus = json.str("status"),
            totalEpisodes = if (type == "ANIME") media.intOrNull("episodes") else null,
            totalChapters = if (type == "MANGA") media.intOrNull("chapters") else null,
            nextAiringEpisode = media["nextAiringEpisode"]?.takeIf { it != JsonNull }
                ?.jsonObject?.int("episode")?.let { it - 1 },
        )
    }

    fun parseMediaList(data: JsonArray, type: String): List<Media> {
        return data.mapNotNull { entry ->
            try {
                parseMedia(entry.jsonObject)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun parseCharacter(json: JsonObject): Character {
        val node = json["node"]!!.jsonObject
        return Character(
            id = node.int("id"),
            name = node["name"]?.jsonObject?.str("userPreferred"),
            image = node["image"]?.jsonObject?.str("medium"),
            role = json.str("role"),
        )
    }

    // ── JSON helpers ──────────────────────────────────────────
    private fun JsonObject.str(key: String): String? {
        val v = this[key] ?: return null
        if (v == JsonNull) return null
        return v.jsonPrimitive.content.takeIf { it != "null" }
    }

    private fun JsonObject.strOrNull(key: String): String? {
        val v = this[key] ?: return null
        if (v == JsonNull) return null
        val s = v.jsonPrimitive.content
        return s.takeIf { it != "null" }
    }

    private fun JsonObject.int(key: String): Int {
        val v = this[key] ?: return 0
        if (v == JsonNull) return 0
        return v.jsonPrimitive.content.toIntOrNull() ?: 0
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val v = this[key] ?: return null
        if (v == JsonNull) return null
        return v.jsonPrimitive.content.toIntOrNull()
    }
}
