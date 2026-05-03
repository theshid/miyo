package ani.saikou.presentation.screens.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.FeedbackCategory
import ani.saikou.domain.usecase.feedback.SubmitFeedbackUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedbackViewModel(
    private val submitFeedback: SubmitFeedbackUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState

    fun selectCategory(category: FeedbackCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun updateMessage(message: String) {
        _uiState.update { it.copy(message = message, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        val trimmed = state.message.trim()
        if (trimmed.length < MIN_MESSAGE_LENGTH) {
            _uiState.update { it.copy(errorMessage = "Please write at least $MIN_MESSAGE_LENGTH characters.") }
            return
        }
        if (state.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            // submitFeedback suspends — read-modify-write through .update so we
            // don't clobber any field the user touched while the request was
            // in flight (specifically `message`, which Success resets to "").
            submitFeedback(state.category, trimmed)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, didSubmit = true, message = "") }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = e.message ?: "Couldn't send feedback.",
                        )
                    }
                }
        }
    }

    /** Acknowledge the success state so the screen can clear its banner. */
    fun consumeSubmitted() {
        _uiState.update { it.copy(didSubmit = false) }
    }

    companion object {
        private const val MIN_MESSAGE_LENGTH = 10
    }
}

data class FeedbackUiState(
    val category: FeedbackCategory = FeedbackCategory.GENERAL,
    val message: String = "",
    val isSubmitting: Boolean = false,
    val didSubmit: Boolean = false,
    val errorMessage: String? = null,
)
