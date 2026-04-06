package ani.saikou.screens.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.remote.parsers.MangaDexParser
import ani.saikou.di.AppModule
import ani.saikou.domain.model.MangaPage
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

    init {
        loadPages()
    }

    private fun loadPages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 1. Get media info for title search
            val media = repository.getMedia(mediaId)
            val title = media?.nameRomaji ?: media?.name ?: "Unknown"

            _uiState.value = _uiState.value.copy(title = title, chapterTitle = "Chapter $chapterNum")

            // 2. Search MangaDex
            val sources = mangaDex.search(title)
            val source = sources.firstOrNull()
            if (source == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Manga not found on MangaDex")
                return@launch
            }

            // 3. Get chapters
            val chapters = mangaDex.getChapters(source.id)
            val chapter = chapters.find { it.number.toInt() == chapterNum }
            if (chapter == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Chapter $chapterNum not found")
                return@launch
            }

            _uiState.value = _uiState.value.copy(chapterTitle = chapter.name)

            // 4. Get pages
            val pages = mangaDex.getPages(chapter.id)
            _uiState.value = _uiState.value.copy(
                pages = pages,
                totalPages = pages.size,
                isLoading = false,
            )
        }
    }
}

data class ReaderUiState(
    val title: String = "",
    val chapterTitle: String = "",
    val pages: List<MangaPage> = emptyList(),
    val totalPages: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)
