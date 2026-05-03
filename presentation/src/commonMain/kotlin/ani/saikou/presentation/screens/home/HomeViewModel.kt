package ani.saikou.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.event.ListEvent
import ani.saikou.domain.event.ListEventBus
import ani.saikou.domain.model.AnilistFailure
import ani.saikou.domain.model.DayActivity
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.ReadingHistoryItem
import ani.saikou.domain.model.User
import ani.saikou.domain.model.WatchHistoryItem
import ani.saikou.domain.usecase.activity.ObserveActivityCalendarUseCase
import ani.saikou.domain.usecase.anilist.GetHomeAnilistSnapshotUseCase
import ani.saikou.domain.usecase.anilist.RefreshHomeAnilistSnapshotUseCase
import ani.saikou.domain.usecase.history.ObserveChaptersReadCountUseCase
import ani.saikou.domain.usecase.history.ObserveContinueWatchingUseCase
import ani.saikou.domain.usecase.history.ObserveEpisodesWatchedCountUseCase
import ani.saikou.domain.usecase.history.ObserveReadingHistoryUseCase
import ani.saikou.domain.usecase.history.ObserveRecentWatchHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class HomeViewModel(
    private val getAnilistSnapshot: GetHomeAnilistSnapshotUseCase,
    private val refreshAnilistSnapshot: RefreshHomeAnilistSnapshotUseCase,
    private val observeReadingHistory: ObserveReadingHistoryUseCase,
    private val observeContinueWatching: ObserveContinueWatchingUseCase,
    private val observeRecentWatchHistory: ObserveRecentWatchHistoryUseCase,
    private val observeEpisodesWatchedCount: ObserveEpisodesWatchedCountUseCase,
    private val observeChaptersReadCount: ObserveChaptersReadCountUseCase,
    private val observeActivityCalendar: ObserveActivityCalendarUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadHomeData()
        observeReading()
        observeWatching()
        observeLocalCounts()
        observeActivity()
        observeListEvents()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val snapshot = getAnilistSnapshot()
            _uiState.update {
                it.copy(
                    user = snapshot.user,
                    continueWatching = snapshot.continueWatching,
                    continueReading = snapshot.continueReading,
                    recommendations = snapshot.recommendations,
                    airingSchedule = snapshot.airingSchedule,
                    isLoading = false,
                    error = snapshot.failure?.let(::friendlyMessage),
                )
            }
        }
    }

    fun retryLoadHomeData() {
        loadHomeData()
    }

    private fun friendlyMessage(failure: AnilistFailure): String =
        when (failure) {
            is AnilistFailure.Network -> "Couldn't reach AniList. Check your connection."
            is AnilistFailure.Server -> "AniList is having issues (HTTP ${failure.httpStatus})."
            is AnilistFailure.Other -> "Something went wrong loading your home feed."
        }

    /**
     * Listens for mutation events and refreshes only the AniList sections.
     * No refresh happens if no events are emitted — so navigating away
     * and back without changing anything costs zero API calls.
     */
    private fun observeListEvents() {
        viewModelScope.launch {
            ListEventBus.events.collect { event ->
                when (event) {
                    is ListEvent.ListEntryChanged,
                    is ListEvent.ProgressUpdated,
                    is ListEvent.ReadingProgressUpdated,
                    -> refreshAniListSections()
                    is ListEvent.FavoriteToggled -> {
                        // Favorites only show in UserLists, not Home — no-op.
                    }
                }
            }
        }
    }

    private fun refreshAniListSections() {
        viewModelScope.launch {
            val refreshed = refreshAnilistSnapshot()
            _uiState.update {
                it.copy(
                    user = refreshed.user,
                    continueWatching = refreshed.continueWatching,
                    continueReading = refreshed.continueReading,
                    airingSchedule = refreshed.airingSchedule,
                )
            }
        }
    }

    private fun observeReading() {
        viewModelScope.launch {
            observeReadingHistory(10).collect { history ->
                _uiState.update { it.copy(readingHistory = history) }
            }
        }
    }

    private fun observeWatching() {
        viewModelScope.launch {
            observeContinueWatching(10).collect { inProgress ->
                _uiState.update { it.copy(continueWatchingLocal = inProgress) }
            }
        }
        viewModelScope.launch {
            observeRecentWatchHistory(10).collect { recent ->
                _uiState.update { it.copy(watchHistory = recent) }
            }
        }
    }

    private fun observeLocalCounts() {
        viewModelScope.launch {
            observeEpisodesWatchedCount().collect { count ->
                _uiState.update { it.copy(localEpisodesWatched = count) }
            }
        }
        viewModelScope.launch {
            observeChaptersReadCount().collect { count ->
                _uiState.update { it.copy(localChaptersRead = count) }
            }
        }
    }

    private fun observeActivity() {
        viewModelScope.launch {
            observeActivityCalendar().collect { snapshot ->
                _uiState.update {
                    it.copy(
                        activityByDay = snapshot.countsByDay,
                        activitiesByDay = snapshot.activitiesByDay,
                    )
                }
            }
        }
    }
}

data class HomeUiState(
    val user: User? = null,
    val continueWatching: List<Media> = emptyList(),
    val continueReading: List<Media> = emptyList(),
    val recommendations: List<Media> = emptyList(),
    val airingSchedule: List<Media> = emptyList(),
    val readingHistory: List<ReadingHistoryItem> = emptyList(),
    val continueWatchingLocal: List<WatchHistoryItem> = emptyList(),
    val watchHistory: List<WatchHistoryItem> = emptyList(),
    val localEpisodesWatched: Int = 0,
    val localChaptersRead: Int = 0,
    val activityByDay: Map<LocalDate, Int> = emptyMap(),
    val activitiesByDay: Map<LocalDate, List<DayActivity>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
