package ani.saikou.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.remote.parsers.GogoParser
import ani.saikou.di.AppModule
import ani.saikou.domain.model.StreamLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VideoPlayerViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository = AppModule.repository()
    private val gogoParser = GogoParser()

    val mediaId: Int = savedStateHandle["mediaId"] ?: 0
    val episodeNum: Int = savedStateHandle["episodeNum"] ?: 1

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    init {
        loadStream()
    }

    private fun loadStream() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 1. Get media info for title
            val media = repository.getMedia(mediaId)
            val title = media?.nameRomaji ?: media?.name ?: "Unknown"

            _uiState.value = _uiState.value.copy(
                title = title,
                episodeTitle = "Episode $episodeNum",
            )

            // 2. Search Gogo for this anime
            val sources = gogoParser.search(title)
            val source = sources.firstOrNull()
            if (source == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Anime not found on source")
                return@launch
            }

            // 3. Get episodes
            val episodes = gogoParser.getEpisodes(source.slug)
            val episode = episodes.find { it.number == episodeNum.toString() }
            if (episode?.link == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Episode $episodeNum not found")
                return@launch
            }

            // 4. Get stream links
            val links = gogoParser.getStreamLinks(episode.link)
            _uiState.value = _uiState.value.copy(
                streamLinks = links,
                selectedLink = links.firstOrNull(),
                isLoading = false,
            )
        }
    }

    fun selectStream(link: StreamLink) {
        _uiState.value = _uiState.value.copy(selectedLink = link)
    }
}

data class PlayerUiState(
    val title: String = "",
    val episodeTitle: String = "",
    val streamLinks: List<StreamLink> = emptyList(),
    val selectedLink: StreamLink? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)
