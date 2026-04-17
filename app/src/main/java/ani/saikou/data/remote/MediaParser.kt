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
        val nextAiringObj = json["nextAiringEpisode"]?.takeIf { it != JsonNull }?.jsonObject
        val lastAiredEpisode = nextAiringObj?.int("episode")?.let { it - 1 }
        val airingAt = nextAiringObj?.longOrNull("airingAt")?.let { it * 1000 } // seconds → ms

        return Media(
            id = json.int("id"),
            malId = json.intOrNull("idMal"),
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
            userListEntryId = if (hasEntry) listEntry!!.jsonObject.intOrNull("id") else null,
            totalEpisodes = if (type == "ANIME") (json.intOrNull("episodes") ?: lastAiredEpisode) else null,
            totalChapters = if (type == "MANGA") json.intOrNull("chapters") else null,
            nextAiringEpisode = lastAiredEpisode,
            nextAiringEpisodeTime = airingAt,
        )
    }

    fun parseMediaFromListEntry(json: JsonObject, type: String): Media {
        val media = json["media"]!!.jsonObject
        val title = media["title"]!!.jsonObject
        val nextAiringObj = media["nextAiringEpisode"]?.takeIf { it != JsonNull }?.jsonObject
        val lastAiredEpisode = nextAiringObj?.int("episode")?.let { it - 1 }
        val airingAt = nextAiringObj?.longOrNull("airingAt")?.let { it * 1000 }

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
            totalEpisodes = if (type == "ANIME") (media.intOrNull("episodes") ?: lastAiredEpisode) else null,
            totalChapters = if (type == "MANGA") media.intOrNull("chapters") else null,
            nextAiringEpisode = lastAiredEpisode,
            nextAiringEpisodeTime = airingAt,
        )
    }

    /**
     * Parses the full mediaDetails response — includes description, genres,
     * format, season, studio, characters, relations, and recommendations.
     * Falls through to [parseMedia] for all the base fields.
     */
    fun parseMediaDetail(json: JsonObject): Media {
        val base = parseMedia(json)

        // ── Scalar detail fields ─────────────────────────────
        val description = json.strOrNull("description")
        val format = json.strOrNull("format")
        val season = json.strOrNull("season")
        val seasonYear = json.intOrNull("seasonYear")
        val duration = json.intOrNull("duration")
        val genres = json["genres"]?.takeIf { it != JsonNull }
            ?.jsonArray?.mapNotNull { it.jsonPrimitive.content.takeIf { s -> s != "null" } }

        val startDate = json["startDate"]?.takeIf { it != JsonNull }?.jsonObject?.let { d ->
            formatDate(d.intOrNull("year"), d.intOrNull("month"), d.intOrNull("day"))
        }
        val endDate = json["endDate"]?.takeIf { it != JsonNull }?.jsonObject?.let { d ->
            formatDate(d.intOrNull("year"), d.intOrNull("month"), d.intOrNull("day"))
        }

        // ── Studio ───────────────────────────────────────────
        val mainStudio = json["studios"]?.takeIf { it != JsonNull }
            ?.jsonObject?.get("nodes")?.jsonArray
            ?.firstOrNull()?.jsonObject?.str("name")

        // ── Characters ───────────────────────────────────────
        val characters = json["characters"]?.takeIf { it != JsonNull }
            ?.jsonObject?.get("edges")?.jsonArray?.mapNotNull { edge ->
                try { parseCharacter(edge.jsonObject) } catch (_: Exception) { null }
            }

        // ── Relations ────────────────────────────────────────
        val relations = json["relations"]?.takeIf { it != JsonNull }
            ?.jsonObject?.get("edges")?.jsonArray?.mapNotNull { edge ->
                try { parseMedia(edge.jsonObject["node"]!!.jsonObject) } catch (_: Exception) { null }
            }

        // ── Recommendations ──────────────────────────────────
        val recommendations = json["recommendations"]?.takeIf { it != JsonNull }
            ?.jsonObject?.get("nodes")?.jsonArray?.mapNotNull { node ->
                try {
                    val rec = node.jsonObject["mediaRecommendation"]
                    if (rec != null && rec != JsonNull) parseMedia(rec.jsonObject) else null
                } catch (_: Exception) { null }
            }

        return base.copy(
            description = description,
            format = format,
            season = season,
            seasonYear = seasonYear,
            episodeDuration = duration,
            mainStudio = mainStudio,
            genres = genres,
            startDate = startDate,
            endDate = endDate,
            characters = characters,
            relations = relations,
            recommendations = recommendations,
        )
    }

    private fun formatDate(year: Int?, month: Int?, day: Int?): String? {
        if (year == null) return null
        return buildString {
            append(year)
            if (month != null) append("-${"%02d".format(month)}")
            if (day != null) append("-${"%02d".format(day)}")
        }
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

    private fun JsonObject.longOrNull(key: String): Long? {
        val v = this[key] ?: return null
        if (v == JsonNull) return null
        return v.jsonPrimitive.content.toLongOrNull()
    }
}
