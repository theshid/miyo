package ani.saikou.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.di.AppModule
import ani.saikou.domain.model.UserStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StatsViewModel : ViewModel() {

    private val repository = AppModule.repository()

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val stats = repository.getUserStats()
            _uiState.value = StatsUiState(
                stats = stats,
                isLoading = false,
            )
        }
    }
}

data class StatsUiState(
    val stats: UserStats? = null,
    val isLoading: Boolean = true,
)
