package ani.saikou.presentation.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.ChatMessage
import ani.saikou.domain.source.AiChatService
import ani.saikou.domain.usecase.ai.BuildAiUserContextSnippetUseCase
import ani.saikou.domain.usecase.ai.SendAiChatMessageUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiChatViewModel(
    private val sendChatMessage: SendAiChatMessageUseCase,
    private val buildUserContext: BuildAiUserContextSnippetUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState

    private val conversationHistory =
        mutableListOf(
            ChatMessage(role = "system", content = AiChatService.SYSTEM_PROMPT),
        )

    init {
        loadUserContext()
    }

    private fun loadUserContext() {
        viewModelScope.launch {
            val snippet = buildUserContext() ?: return@launch
            conversationHistory.add(ChatMessage(role = "system", content = snippet))
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isLoading) return

        val userMessage = ChatBubble(text = trimmed, isUser = true)
        conversationHistory.add(ChatMessage(role = "user", content = trimmed))
        _uiState.update { it.copy(messages = it.messages + userMessage, isLoading = true) }

        viewModelScope.launch {
            val response = sendChatMessage(conversationHistory)
            conversationHistory.add(ChatMessage(role = "assistant", content = response))
            val aiMessage = ChatBubble(text = response, isUser = false)
            _uiState.update { it.copy(messages = it.messages + aiMessage, isLoading = false) }
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
