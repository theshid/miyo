package ani.saikou.presentation.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.UserStats
import ani.saikou.domain.usecase.anilist.GetUserStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StatsViewModel(
    private val getUserStats: GetUserStatsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    init {
        loadStats()
    }

    fun loadStats() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val stats = getUserStats()
            _uiState.update { it.copy(stats = stats, isLoading = false) }
        }
    }
}

data class StatsUiState(
    val stats: UserStats? = null,
    val isLoading: Boolean = true,
)
