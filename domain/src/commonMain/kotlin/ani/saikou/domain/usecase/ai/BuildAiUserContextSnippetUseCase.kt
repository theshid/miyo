package ani.saikou.domain.usecase.ai

import ani.saikou.domain.repository.AiChatRepository

/**
 * "What does the assistant know about this user?" — returns a one-shot
 * system-message body summarizing the user's current AniList activity, or
 * null when none could be assembled (logged out / no in-progress lists).
 * Callers prepend this once at session start; it isn't refreshed mid-chat.
 */
class BuildAiUserContextSnippetUseCase(
    private val repository: AiChatRepository,
) {
    suspend operator fun invoke(): String? = repository.buildUserContextSnippet()
}
