package ani.saikou.screens.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.components.SourceItem
import ani.saikou.data.remote.parsers.MangaDexParser
import ani.saikou.di.AppModule
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MangaReaderViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository = AppModule.repository()
    private val mangaDex = MangaDexParser()

    val mediaId: Int = savedStateHandle["mediaId"] ?: 0
    val chapterNum: Int = savedStateHandle["chapterNum"] ?: 1

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    private var mangaSources: List<MangaSource> = emptyList()

    init {
        loadSources()
    }

    private fun loadSources() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val media = repository.getMedia(mediaId)
            val title = media?.nameRomaji ?: media?.name ?: "Unknown"
            _uiState.value = _uiState.value.copy(title = title, chapterTitle = "Chapter $chapterNum")

            mangaSources = mangaDex.search(title)
            if (mangaSources.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Manga not found on MangaDex")
                return@launch
            }

            if (mangaSources.size == 1) {
                selectSource(mangaSources.first())
            } else {
                _uiState.value = _uiState.value.copy(
                    showSourceSelector = true,
                    availableSources = mangaSources.map {
                        SourceItem(id = it.id, title = it.title, coverUrl = it.coverUrl)
                    },
                    isLoading = false,
                )
            }
        }
    }

    fun selectSource(source: MangaSource) {
        _uiState.value = _uiState.value.copy(showSourceSelector = false, isLoading = true)
        viewModelScope.launch {
            val chapters = mangaDex.getChapters(source.id)
            val chapter = chapters.find { it.number.toInt() == chapterNum }
            if (chapter == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Chapter $chapterNum not found")
                return@launch
            }

            _uiState.value = _uiState.value.copy(chapterTitle = chapter.name)

            val pages = mangaDex.getPages(chapter.id)
            _uiState.value = _uiState.value.copy(
                pages = pages,
                totalPages = pages.size,
                isLoading = false,
            )
        }
    }

    fun selectSourceById(id: String) {
        val source = mangaSources.find { it.id == id } ?: return
        selectSource(source)
    }

    fun dismissSourceSelector() {
        _uiState.value = _uiState.value.copy(showSourceSelector = false)
        mangaSources.firstOrNull()?.let { selectSource(it) }
    }
}

data class ReaderUiState(
    val title: String = "",
    val chapterTitle: String = "",
    val pages: List<MangaPage> = emptyList(),
    val totalPages: Int = 0,
    val availableSources: List<SourceItem> = emptyList(),
    val showSourceSelector: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)
