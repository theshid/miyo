package ani.saikou.domain.repository

import ani.saikou.domain.model.ChatMessage

/**
 * Orchestrates AI chat: forwards conversation turns to the configured
 * [ani.saikou.domain.source.AiChatService] and assembles personalization
 * snippets from the user's AniList data so the assistant can give
 * context-aware replies.
 */
interface AiChatRepository {
    /** Send a full conversation history; the service prepends its system prompt internally. */
    suspend fun chat(messages: List<ChatMessage>): String

    /**
     * Build a one-shot "user context" system message body from the signed-in
     * user's currently-watching / currently-reading lists. Returns null when
     * no useful context could be collected (logged out, network failure with
     * no cache) — callers should skip prepending it rather than fail the chat.
     */
    suspend fun buildUserContextSnippet(): String?
}
