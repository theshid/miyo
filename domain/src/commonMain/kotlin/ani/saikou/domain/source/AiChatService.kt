package ani.saikou.domain.source

import ani.saikou.domain.model.ChatMessage

/**
 * Outbound AI chat provider. Today there's a single OpenAI-backed impl; the
 * interface lets a future provider (Anthropic, on-device) drop in without
 * the repository or VMs noticing.
 *
 * Errors are swallowed inside the impl and surface as a user-readable
 * fallback string — chat UIs prefer a graceful degraded message over an
 * exception bubbling into the screen state.
 */
interface AiChatService {
    suspend fun chat(messages: List<ChatMessage>): String

    companion object {
        /**
         * Persona + behavior contract for the assistant. Lives in domain
         * because it's product copy, not transport detail; provider impls
         * forward it as the first system message verbatim.
         */
        val SYSTEM_PROMPT =
            """
            You are Miyo AI, a friendly and knowledgeable anime & manga assistant built into the Miyo app.

            You can help users with:
            - Finding anime or manga based on natural language descriptions (mood, themes, similarity to other titles)
            - Summarizing where they left off in a series ("Catch me up")
            - Answering questions about characters, plot, studios, and creators
            - Giving personalized recommendations based on their watch/read history

            Guidelines:
            - Be concise but enthusiastic — match the energy of an anime fan talking to a friend
            - When recommending titles, always include the full title and a one-line pitch
            - If asked to catch someone up, summarize without major spoilers unless they explicitly ask
            - Format lists with bullet points for readability
            - If you don't know something, say so rather than guessing
            - Keep responses under 300 words unless the user asks for more detail
            """.trimIndent()
    }
}
