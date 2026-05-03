package ani.saikou.domain.usecase.ai

import ani.saikou.domain.model.ChatMessage
import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AiChatRepository

/**
 * "Recap up to where the user is" — assembles the spoiler-bounded recap
 * prompt for [media] and asks the AI service for a hook sentence + 3
 * bullets. The strict-format clauses are intentional: the sheet renders
 * the response verbatim, so any preamble or trailing text leaks straight
 * into the UI.
 */
class CatchMeUpUseCase(
    private val repository: AiChatRepository,
) {
    suspend operator fun invoke(media: Media): String {
        val progressType = if (media.type == "MANGA") "chapters" else "episodes"
        val progressNum = media.userProgress ?: 0
        val total = media.totalEpisodes ?: media.totalChapters

        val prompt =
            buildString {
                append("The user is ${if (media.type == "MANGA") "reading" else "watching"} ")
                append("\"${media.displayTitle}\"")
                if (media.nameRomaji != null && media.nameRomaji != media.displayTitle) {
                    append(" (${media.nameRomaji})")
                }
                append(". They are on $progressType $progressNum")
                if (total != null) append(" out of $total")
                append(".\n\n")

                val description = media.description
                if (!description.isNullOrBlank()) {
                    val cleanDesc =
                        description
                            .replace("<br>", "\n")
                            .replace(Regex("<[^>]*>"), "")
                    append("Series synopsis: $cleanDesc\n\n")
                }

                val genres = media.genres
                if (!genres.isNullOrEmpty()) {
                    append("Genres: ${genres.joinToString(", ")}\n\n")
                }

                append("Give them a snappy \"Catch Me Up\" recap up to $progressType $progressNum. ")
                append("Format strictly:\n")
                append("- One short hook sentence (max 25 words) describing where the story stands RIGHT NOW.\n")
                append(
                    "- Then exactly 3 bullet points covering the most important arcs or developments that got them here.\n",
                )
                append("- Each bullet: one sentence, max 30 words.\n\n")
                append("Do NOT spoil anything beyond $progressType $progressNum. ")
                append("No headings, no preamble, no closing remarks — just the hook line and the 3 bullets.")
            }

        val messages =
            listOf(
                ChatMessage(
                    role = "system",
                    content =
                        "You are Miyo AI, an anime & manga assistant. You provide accurate, spoiler-aware recaps. " +
                            "Only summarize up to the point the user has reached. Never reveal future plot points.",
                ),
                ChatMessage(role = "user", content = prompt),
            )

        return repository.chat(messages)
    }
}
