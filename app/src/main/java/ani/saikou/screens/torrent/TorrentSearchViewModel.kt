package ani.saikou.screens.torrent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.remote.torrent.AniDexSource
import ani.saikou.data.remote.torrent.BTDiggSource
import ani.saikou.data.remote.torrent.NyaaSource
import ani.saikou.data.remote.torrent.TorrentSource
import ani.saikou.domain.model.TorrentResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TorrentSearchViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sources: List<TorrentSource> = listOf(NyaaSource(), BTDiggSource(), AniDexSource())

    private val _uiState = MutableStateFlow(TorrentUiState())
    val uiState: StateFlow<TorrentUiState> = _uiState

    private var searchJob: Job? = null
    private var allResults: List<TorrentResult> = emptyList()

    // Pre-fill from nav arg
    val initialQuery: String = savedStateHandle["query"] ?: ""

    init {
        if (initialQuery.isNotEmpty()) {
            updateQuery(initialQuery)
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        debounceSearch()
    }

    fun setSourceFilter(source: String?) {
        _uiState.value = _uiState.value.copy(sourceFilter = source)
        applyFilters()
    }

    fun setQualityFilter(quality: String?) {
        _uiState.value = _uiState.value.copy(qualityFilter = quality)
        applyFilters()
    }

    fun setSortBy(sort: SortOption) {
        _uiState.value = _uiState.value.copy(sortBy = sort)
        applyFilters()
    }

    fun selectResult(result: TorrentResult) {
        _uiState.value = _uiState.value.copy(selectedResult = result)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedResult = null)
    }

    fun retry() {
        search()
    }

    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            search()
        }
    }

    private fun search() {
        val query = _uiState.value.query
        if (query.isBlank()) {
            allResults = emptyList()
            _uiState.value = _uiState.value.copy(results = emptyList(), isLoading = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val results = sources.map { source ->
                async {
                    try {
                        source.search(query)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()

            allResults = results
            _uiState.value = _uiState.value.copy(isLoading = false)
            applyFilters()
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        var filtered = allResults

        // Source filter
        if (state.sourceFilter != null) {
            filtered = filtered.filter { it.source == state.sourceFilter }
        }

        // Quality filter
        if (state.qualityFilter != null) {
            filtered = filtered.filter { it.quality?.resolution == state.qualityFilter }
        }

        // Sort
        filtered = when (state.sortBy) {
            SortOption.SEEDERS -> filtered.sortedByDescending { it.seeders }
            SortOption.SIZE -> filtered.sortedByDescending { parseSizeToBytes(it.size) }
            SortOption.DATE -> filtered.sortedByDescending { it.date }
        }

        _uiState.value = state.copy(results = filtered)
    }

    private fun parseSizeToBytes(size: String): Long {
        val num = Regex("""[\d.]+""").find(size)?.value?.toDoubleOrNull() ?: return 0
        return when {
            size.contains("GiB", true) || size.contains("GB", true) -> (num * 1_073_741_824).toLong()
            size.contains("MiB", true) || size.contains("MB", true) -> (num * 1_048_576).toLong()
            size.contains("KiB", true) || size.contains("KB", true) -> (num * 1024).toLong()
            else -> num.toLong()
        }
    }
}

data class TorrentUiState(
    val query: String = "",
    val results: List<TorrentResult> = emptyList(),
    val sourceFilter: String? = null,
    val qualityFilter: String? = null,
    val sortBy: SortOption = SortOption.SEEDERS,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedResult: TorrentResult? = null,
)

enum class SortOption { SEEDERS, SIZE, DATE }
