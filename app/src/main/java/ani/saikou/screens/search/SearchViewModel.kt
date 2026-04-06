package ani.saikou.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val repository = AppModule.repository()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null
    private var currentPage = 1

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        debounceSearch()
    }

    fun updateType(type: String) {
        _uiState.value = _uiState.value.copy(type = type)
        search()
    }

    fun toggleGenre(genre: String) {
        val current = _uiState.value.selectedGenres.toMutableList()
        if (genre in current) current.remove(genre) else current.add(genre)
        _uiState.value = _uiState.value.copy(selectedGenres = current)
        search()
    }

    fun updateSort(sort: String?) {
        _uiState.value = _uiState.value.copy(sort = sort)
        search()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedGenres = emptyList(),
            sort = null,
        )
        search()
    }

    fun toggleGridView() {
        _uiState.value = _uiState.value.copy(isGridView = !_uiState.value.isGridView)
    }

    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            search()
        }
    }

    fun search() {
        val state = _uiState.value
        if (state.query.isBlank()) {
            _uiState.value = state.copy(results = emptyList(), totalFound = 0)
            return
        }
        currentPage = 1
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            val results = repository.search(
                query = state.query,
                type = state.type,
                genres = state.selectedGenres.ifEmpty { null },
                sort = state.sort,
                page = currentPage,
            )
            _uiState.value = _uiState.value.copy(
                results = results,
                totalFound = results.size,
                isLoading = false,
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.query.isBlank()) return
        viewModelScope.launch {
            currentPage++
            val more = repository.search(
                query = state.query,
                type = state.type,
                genres = state.selectedGenres.ifEmpty { null },
                sort = state.sort,
                page = currentPage,
            )
            _uiState.value = _uiState.value.copy(
                results = _uiState.value.results + more,
                totalFound = _uiState.value.results.size + more.size,
            )
        }
    }

    companion object {
        val GENRES = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy",
            "Horror", "Mecha", "Music", "Mystery", "Psychological",
            "Romance", "Sci-Fi", "Slice of Life", "Sports",
            "Supernatural", "Thriller",
        )
        val SORT_OPTIONS = mapOf(
            "Popularity" to "POPULARITY_DESC",
            "Score" to "SCORE_DESC",
            "Trending" to "TRENDING_DESC",
            "A-Z" to "TITLE_ENGLISH",
            "Z-A" to "TITLE_ENGLISH_DESC",
        )
    }
}

data class SearchUiState(
    val query: String = "",
    val type: String = "ANIME",
    val selectedGenres: List<String> = emptyList(),
    val sort: String? = null,
    val results: List<Media> = emptyList(),
    val totalFound: Int = 0,
    val isLoading: Boolean = false,
    val isGridView: Boolean = true,
)
