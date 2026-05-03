package ani.saikou.domain.usecase.ai

import ani.saikou.domain.model.ChatMessage
import ani.saikou.domain.repository.AiChatRepository

/**
 * "Send the next chat turn" — accepts the full conversation history (system
 * + prior turns + the new user message) and returns the assistant's reply.
 * The caller (VM) owns history; this is intentionally stateless.
 */
class SendAiChatMessageUseCase(
    private val repository: AiChatRepository,
) {
    suspend operator fun invoke(messages: List<ChatMessage>): String = repository.chat(messages)
}
