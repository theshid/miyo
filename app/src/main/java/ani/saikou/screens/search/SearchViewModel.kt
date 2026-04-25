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

    // Per-session cache: avoids repeat API calls for the same query
    // (e.g. "one pie" → "one piec" → back to "one pie" reuses cached result)
    private val searchCache = mutableMapOf<String, List<Media>>()

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
        val state = _uiState.value
        // Don't fire API calls when there's nothing to search by — empty query
        // AND no filters means we'd just hit the AniList rate-limit for empty
        // results. With at least one filter (genre or sort), browse without a
        // typed query is the whole point.
        if (!hasAnyFilter(state)) {
            _uiState.value = state.copy(results = emptyList(), totalFound = 0, isLoading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            search()
        }
    }

    fun search() {
        val state = _uiState.value
        val query = state.query.trim()
        if (!hasAnyFilter(state)) {
            _uiState.value = state.copy(results = emptyList(), totalFound = 0, isLoading = false)
            return
        }

        // Cache key accounts for filters too
        val cacheKey = "${state.type}|$query|${state.selectedGenres.sorted()}|${state.sort}"

        currentPage = 1

        // Serve from cache immediately if hit
        searchCache[cacheKey]?.let { cached ->
            _uiState.value = state.copy(
                results = cached,
                totalFound = cached.size,
                isLoading = false,
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            val results = repository.search(
                query = query,
                type = state.type,
                genres = state.selectedGenres.ifEmpty { null },
                sort = state.sort,
                page = currentPage,
            )
            searchCache[cacheKey] = results
            // Keep cache bounded
            if (searchCache.size > MAX_CACHE_ENTRIES) {
                searchCache.remove(searchCache.keys.first())
            }
            _uiState.value = _uiState.value.copy(
                results = results,
                totalFound = results.size,
                isLoading = false,
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !hasAnyFilter(state)) return
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

    /** True iff there's something to search by — typed query, picked genre, or sort. */
    private fun hasAnyFilter(state: SearchUiState): Boolean {
        return state.query.trim().length >= MIN_QUERY_LENGTH ||
            state.selectedGenres.isNotEmpty() ||
            state.sort != null
    }

    companion object {
        private const val MIN_QUERY_LENGTH = 1
        private const val MAX_CACHE_ENTRIES = 50

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
