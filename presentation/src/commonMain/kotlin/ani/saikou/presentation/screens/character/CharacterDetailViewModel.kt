package ani.saikou.presentation.screens.character

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.CharacterDetail
import ani.saikou.domain.usecase.anilist.GetCharacterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CharacterDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val getCharacter: GetCharacterUseCase,
) : ViewModel() {
    private val characterId: Int = savedStateHandle["id"] ?: 0

    private val _uiState = MutableStateFlow(CharacterUiState())
    val uiState: StateFlow<CharacterUiState> = _uiState

    init {
        load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val character = getCharacter(characterId)
            _uiState.update { it.copy(character = character, isLoading = false) }
        }
    }
}

data class CharacterUiState(
    val character: CharacterDetail? = null,
    val isLoading: Boolean = true,
)
