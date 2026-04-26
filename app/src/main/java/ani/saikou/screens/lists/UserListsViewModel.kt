package ani.saikou.screens.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.local.ListEvent
import ani.saikou.data.local.ListEventBus
import ani.saikou.data.local.MangaChapterCountCache
import ani.saikou.data.remote.parsers.MangaDexParser
import ani.saikou.data.remote.parsers.MangaPillParser
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class UserListsViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository = AppModule.repository()
    private val type: String = savedStateHandle["type"] ?: "ANIME"

    private val _uiState = MutableStateFlow(UserListsUiState(type = type))
    val uiState: StateFlow<UserListsUiState> = _uiState

    // Limit concurrent source probes so we don't hammer MangaDex/MangaPill
    // when a user has many AniList-null titles (licensed/on-hiatus manga).
    private val probeSemaphore = Semaphore(3)
    private val probesInFlight = mutableSetOf<Int>()

    init {
        loadList(tabs.first())
        observeListEvents()
    }

    private fun observeListEvents() {
        viewModelScope.launch {
            ListEventBus.events.collect { event ->
                when (event) {
                    is ListEvent.ListEntryChanged,
                    is ListEvent.ProgressUpdated,
                    is ListEvent.ReadingProgressUpdated -> refresh()
                    is ListEvent.FavoriteToggled -> {
                        if (_uiState.value.selectedTab == FAVORITES_TAB) refresh()
                    }
                }
            }
        }
    }

    fun selectTab(tab: String) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        loadList(tab)
    }

    /** Re-fetch the currently-selected tab (called on screen resume). */
    fun refresh() {
        loadList(_uiState.value.selectedTab)
    }

    fun updateEntry(mediaId: Int, progress: Int?, score: Int?, status: String?) {
        viewModelScope.launch {
            repository.editListEntry(mediaId, progress, score, status)
            ListEventBus.emit(ListEvent.ListEntryChanged(mediaId, status))
            loadList(_uiState.value.selectedTab) // refresh
        }
    }

    private fun loadList(tab: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val items = if (tab == FAVORITES_TAB) {
                repository.getUserFavorites(type)
            } else {
                val anilistStatus = statusMap[tab] ?: "CURRENT"
                if (type == "ANIME") {
                    repository.getUserAnimeList(anilistStatus)
                } else {
                    repository.getUserMangaList(anilistStatus)
                }
            }
            _uiState.value = _uiState.value.copy(
                items = items,
                isLoading = false,
            )
            // For manga whose AniList chapter count is null/0, kick off a
            // background source probe and stash the real number in the cache.
            // Subsequent renders pick it up reactively via MangaChapterCountCache.counts.
            if (type == "MANGA") probeMissingChapterCounts(items)
        }
    }

    private fun probeMissingChapterCounts(entries: List<Media>) {
        val targets = entries.filter { entry ->
            entry.totalChapters == null || entry.totalChapters == 0
        }.filter { entry ->
            // Skip ones we already know — same-session cache hits, or a
            // probe currently in flight from another tab/screen.
            MangaChapterCountCache.get(entry.id) == null && entry.id !in probesInFlight
        }
        if (targets.isEmpty()) return
        for (entry in targets) {
            probesInFlight += entry.id
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    probeSemaphore.withPermit {
                        val count = resolveChapterCount(entry.nameRomaji ?: entry.name ?: return@withPermit)
                        if (count != null) MangaChapterCountCache.put(entry.id, count)
                    }
                } finally {
                    probesInFlight -= entry.id
                }
            }
        }
    }

    /**
     * Mirrors the detail screen's logic: prefer an exact title match on
     * MangaDex, take its `lastChapter` hint or hosted count, then probe
     * MangaPill if needed and pick whichever gives the larger number.
     */
    private suspend fun resolveChapterCount(title: String): Int? = withContext(Dispatchers.IO) {
        val dex = runCatching {
            val sources = MangaDexParser().search(title)
            val picked = sources.firstOrNull { it.title.trim().equals(title.trim(), ignoreCase = true) }
                ?: sources.firstOrNull()
            picked?.let { src ->
                val chapters = runCatching { MangaDexParser().getChapters(src.id) }.getOrDefault(emptyList())
                Pair(chapters.lastOrNull()?.number?.toInt() ?: 0, src.totalChapterHint ?: 0)
            }
        }.getOrNull() ?: Pair(0, 0)
        val dexCount = dex.first
        val dexHint = dex.second
        val needsPill = dexCount == 0 || (dexHint > 0 && dexCount < dexHint * 0.9)
        val pillCount = if (needsPill) {
            runCatching {
                val sources = MangaPillParser().search(title)
                val picked = sources.firstOrNull { it.title.trim().equals(title.trim(), ignoreCase = true) }
                    ?: sources.firstOrNull()
                picked?.let { src ->
                    runCatching { MangaPillParser().getChapters(src.id) }.getOrDefault(emptyList())
                        .lastOrNull()?.number?.toInt() ?: 0
                } ?: 0
            }.getOrDefault(0)
        } else 0
        listOf(dexCount, dexHint, pillCount).maxOrNull()?.takeIf { it > 0 }
    }

    companion object {
        const val FAVORITES_TAB = "Favorites"
        val tabs = listOf("Watching", "Completed", "Paused", "Planning", "Dropped", FAVORITES_TAB)
        // Only the list-status tabs — excludes Favorites, which isn't a MediaListStatus.
        val statusMap = mapOf(
            "Watching" to "CURRENT",
            "Completed" to "COMPLETED",
            "Paused" to "PAUSED",
            "Planning" to "PLANNING",
            "Dropped" to "DROPPED",
        )
    }
}

data class UserListsUiState(
    val type: String = "ANIME",
    val selectedTab: String = "Watching",
    val items: List<Media> = emptyList(),
    val isLoading: Boolean = true,
)
