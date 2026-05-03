package ani.saikou.data.repository

import ani.saikou.domain.model.ChatMessage
import ani.saikou.domain.repository.AiChatRepository
import ani.saikou.domain.repository.AnilistRepository
import ani.saikou.domain.source.AiChatService

/**
 * Bridges the chat service to the user's AniList data. The transport (HTTP,
 * OpenAI) is fronted by [AiChatService]; the personalization material is
 * built once per session by reading the signed-in user's currently-watching
 * / currently-reading lists.
 */
class AiChatRepositoryImpl(
    private val service: AiChatService,
    private val anilistRepository: AnilistRepository,
) : AiChatRepository {
    override suspend fun chat(messages: List<ChatMessage>): String = service.chat(messages)

    override suspend fun buildUserContextSnippet(): String? {
        val parts = mutableListOf<String>()
        try {
            anilistRepository.getUserData()?.let { parts += "User: ${it.name}" }

            val animeList = anilistRepository.getUserAnimeList("CURRENT")
            if (animeList.isNotEmpty()) {
                val watching =
                    animeList.take(MAX_LIST_ITEMS).joinToString(", ") {
                        "${it.displayTitle} (${it.episodeProgress ?: "?"})"
                    }
                parts += "Currently watching: $watching"
            }

            val mangaList = anilistRepository.getUserMangaList("CURRENT")
            if (mangaList.isNotEmpty()) {
                val reading =
                    mangaList.take(MAX_LIST_ITEMS).joinToString(", ") {
                        "${it.displayTitle} (${it.episodeProgress ?: "?"})"
                    }
                parts += "Currently reading: $reading"
            }
        } catch (_: Exception) {
            // Non-critical — AI works fine without user context.
        }

        if (parts.isEmpty()) return null
        return "User context from their AniList account:\n${parts.joinToString("\n")}"
    }

    companion object {
        // Cap the per-list slice fed to the model so we don't blow the
        // context window on power users with hundreds of in-progress titles.
        private const val MAX_LIST_ITEMS = 15
    }
}
