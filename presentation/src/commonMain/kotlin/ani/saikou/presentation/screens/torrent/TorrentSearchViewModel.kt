package ani.saikou.presentation.screens.torrent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.TorrentResult
import ani.saikou.domain.usecase.torrents.SearchTorrentsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TorrentSearchViewModel(
    savedStateHandle: SavedStateHandle,
    private val searchTorrents: SearchTorrentsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TorrentUiState())
    val uiState: StateFlow<TorrentUiState> = _uiState

    private var searchJob: Job? = null
    private var allResults: List<TorrentResult> = emptyList()

    /** Pre-fill from nav arg — read once, exposed so the screen can seed its TextField. */
    val initialQuery: String = savedStateHandle["query"] ?: ""

    init {
        if (initialQuery.isNotEmpty()) {
            updateQuery(initialQuery)
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        debounceSearch()
    }

    fun setSourceFilter(source: String?) {
        _uiState.update { it.copy(sourceFilter = source) }
        applyFilters()
    }

    fun setQualityFilter(quality: String?) {
        _uiState.update { it.copy(qualityFilter = quality) }
        applyFilters()
    }

    fun setSortBy(sort: SortOption) {
        _uiState.update { it.copy(sortBy = sort) }
        applyFilters()
    }

    fun selectResult(result: TorrentResult) {
        _uiState.update { it.copy(selectedResult = result) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedResult = null) }
    }

    fun retry() {
        search()
    }

    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob =
            viewModelScope.launch {
                delay(DEBOUNCE_MS)
                search()
            }
    }

    private fun search() {
        val query = _uiState.value.query
        if (query.isBlank()) {
            allResults = emptyList()
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            allResults = searchTorrents(query)
            _uiState.update { it.copy(isLoading = false) }
            applyFilters()
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        var filtered = allResults

        if (state.sourceFilter != null) {
            filtered = filtered.filter { it.source == state.sourceFilter }
        }
        if (state.qualityFilter != null) {
            filtered = filtered.filter { it.quality?.resolution == state.qualityFilter }
        }
        filtered =
            when (state.sortBy) {
                SortOption.SEEDERS -> filtered.sortedByDescending { it.seeders }
                SortOption.SIZE -> filtered.sortedByDescending { parseSizeToBytes(it.size) }
                SortOption.DATE -> filtered.sortedByDescending { it.date }
            }

        _uiState.update { it.copy(results = filtered) }
    }

    private fun parseSizeToBytes(size: String): Long {
        val num = Regex("""[\d.]+""").find(size)?.value?.toDoubleOrNull() ?: return 0
        return when {
            size.contains("GiB", true) || size.contains("GB", true) -> (num * BYTES_PER_GIB).toLong()
            size.contains("MiB", true) || size.contains("MB", true) -> (num * BYTES_PER_MIB).toLong()
            size.contains("KiB", true) || size.contains("KB", true) -> (num * BYTES_PER_KIB).toLong()
            else -> num.toLong()
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 500L
        private const val BYTES_PER_KIB = 1024.0
        private const val BYTES_PER_MIB = 1_048_576.0
        private const val BYTES_PER_GIB = 1_073_741_824.0
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
