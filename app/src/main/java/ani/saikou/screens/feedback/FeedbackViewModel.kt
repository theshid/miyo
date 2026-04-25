package ani.saikou.screens.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.remote.FeedbackService
import ani.saikou.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FeedbackViewModel : ViewModel() {

    private val service = AppModule.feedbackService()

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState

    fun selectCategory(category: FeedbackService.Category) {
        _uiState.value = _uiState.value.copy(category = category)
    }

    fun updateMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message, errorMessage = null)
    }

    fun submit() {
        val state = _uiState.value
        val trimmed = state.message.trim()
        if (trimmed.length < MIN_MESSAGE_LENGTH) {
            _uiState.value = state.copy(errorMessage = "Please write at least $MIN_MESSAGE_LENGTH characters.")
            return
        }
        if (state.isSubmitting) return

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = service.submit(state.category, trimmed)) {
                is FeedbackService.Result.Success -> {
                    _uiState.value = state.copy(isSubmitting = false, didSubmit = true, message = "")
                }
                is FeedbackService.Result.Failure -> {
                    _uiState.value = state.copy(isSubmitting = false, errorMessage = result.reason)
                }
            }
        }
    }

    /** Acknowledge the success state so the screen can clear its banner. */
    fun consumeSubmitted() {
        _uiState.value = _uiState.value.copy(didSubmit = false)
    }

    companion object {
        private const val MIN_MESSAGE_LENGTH = 10
    }
}

data class FeedbackUiState(
    val category: FeedbackService.Category = FeedbackService.Category.GENERAL,
    val message: String = "",
    val isSubmitting: Boolean = false,
    val didSubmit: Boolean = false,
    val errorMessage: String? = null,
)
