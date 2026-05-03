package ani.saikou.domain.model

/**
 * One turn in an AI conversation. Roles match the OpenAI API vocabulary
 * ("system" / "user" / "assistant") so providers can map straight through;
 * if a future provider uses a different naming, the mapping happens in the
 * service impl, not here.
 */
data class ChatMessage(
    val role: String,
    val content: String,
)
