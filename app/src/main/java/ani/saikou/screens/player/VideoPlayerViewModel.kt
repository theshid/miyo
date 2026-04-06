package ani.saikou.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.components.SourceItem
import ani.saikou.data.remote.parsers.GogoParser
import ani.saikou.di.AppModule
import ani.saikou.domain.model.AnimeSource
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

    private var animeSources: List<AnimeSource> = emptyList()

    init {
        loadSources()
    }

    private fun loadSources() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val media = repository.getMedia(mediaId)
            val title = media?.nameRomaji ?: media?.name ?: "Unknown"

            _uiState.value = _uiState.value.copy(
                title = title,
                episodeTitle = "Episode $episodeNum",
                coverUrl = media?.banner ?: media?.cover,
            )

            animeSources = gogoParser.search(title)
            if (animeSources.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Anime not found on source")
                return@launch
            }

            if (animeSources.size == 1) {
                // Auto-select if only one result
                selectSource(animeSources.first())
            } else {
                // Show selector
                _uiState.value = _uiState.value.copy(
                    showSourceSelector = true,
                    availableSources = animeSources.map {
                        SourceItem(id = it.slug, title = it.name, coverUrl = it.cover)
                    },
                    isLoading = false,
                )
            }
        }
    }

    fun selectSource(source: AnimeSource) {
        _uiState.value = _uiState.value.copy(showSourceSelector = false, isLoading = true)
        viewModelScope.launch {
            val episodes = gogoParser.getEpisodes(source.slug)
            val episode = episodes.find { it.number == episodeNum.toString() }
            if (episode?.link == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Episode $episodeNum not found")
                return@launch
            }

            val links = gogoParser.getStreamLinks(episode.link)
            _uiState.value = _uiState.value.copy(
                streamLinks = links,
                selectedLink = links.firstOrNull(),
                isLoading = false,
            )
        }
    }

    fun selectSourceById(id: String) {
        val source = animeSources.find { it.slug == id } ?: return
        selectSource(source)
    }

    fun selectStream(link: StreamLink) {
        _uiState.value = _uiState.value.copy(selectedLink = link)
    }

    fun dismissSourceSelector() {
        _uiState.value = _uiState.value.copy(showSourceSelector = false)
        // Auto-select first if dismissed
        animeSources.firstOrNull()?.let { selectSource(it) }
    }
}

data class PlayerUiState(
    val title: String = "",
    val episodeTitle: String = "",
    val coverUrl: String? = null,
    val streamLinks: List<StreamLink> = emptyList(),
    val selectedLink: StreamLink? = null,
    val availableSources: List<SourceItem> = emptyList(),
    val showSourceSelector: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)
