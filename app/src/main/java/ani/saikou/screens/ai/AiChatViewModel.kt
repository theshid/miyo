package ani.saikou.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.remote.OpenAiService
import ani.saikou.domain.repository.AnilistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AiChatViewModel(
    private val openAiService: OpenAiService,
    private val repository: AnilistRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState

    private val conversationHistory =
        mutableListOf(
            OpenAiService.ChatMessage(role = "system", content = OpenAiService.SYSTEM_PROMPT),
        )

    init {
        loadUserContext()
    }

    private fun loadUserContext() {
        viewModelScope.launch {
            try {
                val user = repository.getUserData()
                val animeList = repository.getUserAnimeList("CURRENT")
                val mangaList = repository.getUserMangaList("CURRENT")

                val contextParts = mutableListOf<String>()

                user?.let { contextParts.add("User: ${it.name}") }

                if (animeList.isNotEmpty()) {
                    val watching =
                        animeList.take(15).joinToString(", ") {
                            "${it.displayTitle} (${it.episodeProgress ?: "?"})"
                        }
                    contextParts.add("Currently watching: $watching")
                }

                if (mangaList.isNotEmpty()) {
                    val reading =
                        mangaList.take(15).joinToString(", ") {
                            "${it.displayTitle} (${it.episodeProgress ?: "?"})"
                        }
                    contextParts.add("Currently reading: $reading")
                }

                if (contextParts.isNotEmpty()) {
                    conversationHistory.add(
                        OpenAiService.ChatMessage(
                            role = "system",
                            content = "User context from their AniList account:\n${contextParts.joinToString("\n")}",
                        ),
                    )
                }
            } catch (_: Exception) {
                // Non-critical — AI works fine without user context
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isLoading) return

        val userMessage = ChatBubble(text = trimmed, isUser = true)

        conversationHistory.add(OpenAiService.ChatMessage(role = "user", content = trimmed))

        _uiState.value =
            _uiState.value.copy(
                messages = _uiState.value.messages + userMessage,
                isLoading = true,
            )

        viewModelScope.launch {
            val response = openAiService.chat(conversationHistory)

            conversationHistory.add(OpenAiService.ChatMessage(role = "assistant", content = response))

            val aiMessage = ChatBubble(text = response, isUser = false)
            _uiState.value =
                _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isLoading = false,
                )
        }
    }
}

data class ChatBubble(
    val text: String,
    val isUser: Boolean,
)

data class AiChatUiState(
    val messages: List<ChatBubble> = emptyList(),
    val isLoading: Boolean = false,
)
