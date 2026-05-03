package ani.saikou.presentation.screens.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.cache.MangaChapterCountCache
import ani.saikou.domain.event.ListEvent
import ani.saikou.domain.event.ListEventBus
import ani.saikou.domain.model.Media
import ani.saikou.domain.usecase.anilist.EditListEntryUseCase
import ani.saikou.domain.usecase.anilist.GetUserAnimeListUseCase
import ani.saikou.domain.usecase.anilist.GetUserFavoritesUseCase
import ani.saikou.domain.usecase.anilist.GetUserMangaListUseCase
import ani.saikou.domain.usecase.anilist.ResolveChapterCountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class UserListsViewModel(
    savedStateHandle: SavedStateHandle,
    private val getUserAnimeList: GetUserAnimeListUseCase,
    private val getUserMangaList: GetUserMangaListUseCase,
    private val getUserFavorites: GetUserFavoritesUseCase,
    private val editListEntry: EditListEntryUseCase,
    private val resolveChapterCount: ResolveChapterCountUseCase,
) : ViewModel() {
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
                    is ListEvent.ReadingProgressUpdated,
                    -> refresh()
                    is ListEvent.FavoriteToggled -> {
                        if (_uiState.value.selectedTab == FAVORITES_TAB) refresh()
                    }
                }
            }
        }
    }

    fun selectTab(tab: String) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
        loadList(tab)
    }

    /** Re-fetch the currently-selected tab (called on screen resume). */
    fun refresh() {
        loadList(_uiState.value.selectedTab)
    }

    fun updateEntry(
        mediaId: Int,
        progress: Int?,
        score: Int?,
        status: String?,
    ) {
        viewModelScope.launch {
            editListEntry(mediaId, progress, score, status)
            ListEventBus.emit(ListEvent.ListEntryChanged(mediaId, status))
            loadList(_uiState.value.selectedTab) // refresh
        }
    }

    private fun loadList(tab: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val items =
                if (tab == FAVORITES_TAB) {
                    getUserFavorites(type)
                } else {
                    val anilistStatus = statusMap[tab] ?: "CURRENT"
                    if (type == "ANIME") {
                        getUserAnimeList(anilistStatus)
                    } else {
                        getUserMangaList(anilistStatus)
                    }
                }
            _uiState.update { it.copy(items = items, isLoading = false) }
            // For manga whose AniList chapter count is null/0, kick off a
            // background source probe and stash the real number in the cache.
            // Subsequent renders pick it up reactively via MangaChapterCountCache.counts.
            if (type == "MANGA") probeMissingChapterCounts(items)
        }
    }

    private fun probeMissingChapterCounts(entries: List<Media>) {
        val targets =
            entries
                .filter { entry ->
                    entry.totalChapters == null || entry.totalChapters == 0
                }.filter { entry ->
                    // Skip ones we already know — same-session cache hits, or a
                    // probe currently in flight from another tab/screen.
                    MangaChapterCountCache.get(entry.id) == null && entry.id !in probesInFlight
                }
        if (targets.isEmpty()) return
        for (entry in targets) {
            probesInFlight += entry.id
            viewModelScope.launch {
                try {
                    probeSemaphore.withPermit {
                        val title = entry.nameRomaji ?: entry.name ?: return@withPermit
                        val count = resolveChapterCount(title)
                        if (count != null) MangaChapterCountCache.put(entry.id, count)
                    }
                } finally {
                    probesInFlight -= entry.id
                }
            }
        }
    }

    companion object {
        const val FAVORITES_TAB = "Favorites"
        val tabs = listOf("Watching", "Completed", "Paused", "Planning", "Dropped", FAVORITES_TAB)

        // Only the list-status tabs — excludes Favorites, which isn't a MediaListStatus.
        val statusMap =
            mapOf(
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
