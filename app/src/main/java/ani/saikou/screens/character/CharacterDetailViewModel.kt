package ani.saikou.screens.character

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.CharacterDetail
import ani.saikou.domain.repository.AnilistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CharacterDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: AnilistRepository,
) : ViewModel() {

    private val characterId: Int = savedStateHandle["id"] ?: 0

    private val _uiState = MutableStateFlow(CharacterUiState())
    val uiState: StateFlow<CharacterUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val character = repository.getCharacter(characterId)
            _uiState.value = CharacterUiState(
                character = character,
                isLoading = false,
            )
        }
    }
}

data class CharacterUiState(
    val character: CharacterDetail? = null,
    val isLoading: Boolean = true,
)
