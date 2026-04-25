package ani.saikou.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.local.ListEvent
import ani.saikou.data.local.ListEventBus
import ani.saikou.data.local.db.ReadingHistoryEntity
import ani.saikou.data.local.db.WatchHistoryEntity
import ani.saikou.data.remote.AnilistFailure
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel : ViewModel() {

    private val repository = AppModule.repository()
    private val readingHistoryDao = AppModule.readingHistoryDao()
    private val watchHistoryDao = AppModule.watchHistoryDao()
    private val activityDao = AppModule.activityEventDao()
    private val api = AppModule.anilistApi()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadHomeData()
        observeReadingHistory()
        observeWatchHistory()
        observeLocalStats()
        observeActivity()
        observeListEvents()
    }

    private fun observeActivity() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            // Fetch the whole current year so the user can browse any month
            // via the heatmap's navigation arrows without a second round-trip.
            val sinceMs = LocalDate.now()
                .withDayOfYear(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            activityDao.getSince(sinceMs).collect { events ->
                val byDay = events.groupBy { event ->
                    Instant.ofEpochMilli(event.timestampMs).atZone(zone).toLocalDate()
                }
                val counts = byDay.mapValues { (_, list) -> list.size }
                // Build the per-day list of "what you actually did" for the
                // heatmap menu. Drop session events and rows missing media
                // context (pre-v8 schema rows have NULLs).
                val activities = byDay.mapValues { (_, list) ->
                    list.mapNotNull { event ->
                        val title = event.mediaTitle ?: return@mapNotNull null
                        val mediaId = event.mediaId ?: return@mapNotNull null
                        when (event.type) {
                            "watch" -> ani.saikou.components.DayActivity(
                                mediaId = mediaId,
                                title = title,
                                coverUrl = event.coverUrl,
                                kind = ani.saikou.components.DayActivity.Kind.WATCHED,
                                number = event.episodeNumber ?: 0,
                                timestampMs = event.timestampMs,
                            )
                            "read" -> ani.saikou.components.DayActivity(
                                mediaId = mediaId,
                                title = title,
                                coverUrl = event.coverUrl,
                                kind = ani.saikou.components.DayActivity.Kind.READ,
                                number = event.chapterNumber ?: 0,
                                timestampMs = event.timestampMs,
                            )
                            else -> null
                        }
                    }
                        // De-duplicate: a single chapter/episode can fire
                        // multiple events as the user swaps pages or replays;
                        // we only want one row per (media, kind, number).
                        .distinctBy { Triple(it.mediaId, it.kind, it.number) }
                        .sortedByDescending { it.timestampMs }
                }
                _uiState.value = _uiState.value.copy(
                    activityByDay = counts,
                    activitiesByDay = activities,
                )
            }
        }
    }

    /**
     * Listens for mutation events from other ViewModels and refreshes only the
     * relevant sections. No refresh happens if no events are emitted — so
     * navigating to Search and back without changing anything costs zero API calls.
     */
    private fun observeListEvents() {
        viewModelScope.launch {
            ListEventBus.events.collect { event ->
                when (event) {
                    is ListEvent.ListEntryChanged -> {
                        // Status changed or removed → refresh watch/read lists + user stats
                        refreshAniListSections()
                    }
                    is ListEvent.ProgressUpdated -> {
                        // Episode progress synced → refresh watching list + user stats
                        refreshAniListSections()
                    }
                    is ListEvent.ReadingProgressUpdated -> {
                        // Chapter progress synced → refresh reading list + user stats
                        refreshAniListSections()
                    }
                    is ListEvent.FavoriteToggled -> {
                        // Favorites changed — no need to refresh the full list
                        // (favorites are only shown in UserLists, not Home)
                    }
                }
            }
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val userDeferred = async { repository.getUserData() }
            val watchingDeferred = async { repository.getUserAnimeList("CURRENT") + repository.getUserAnimeList("REPEATING") }
            val readingDeferred = async { repository.getUserMangaList("CURRENT") + repository.getUserMangaList("REPEATING") }
            val recommendationsDeferred = async { repository.getRecommendations() }

            val watching = watchingDeferred.await()
            val reading = readingDeferred.await()
            val recommendations = recommendationsDeferred.await()
            val user = userDeferred.await()

            // Derive airing schedule: shows on user's CURRENT list that have a
            // future airing time, sorted soonest-first.
            val now = System.currentTimeMillis()
            val airing = watching
                .filter { it.nextAiringEpisodeTime != null && it.nextAiringEpisodeTime > now }
                .sortedBy { it.nextAiringEpisodeTime }

            // Surface a load error only if every AniList call effectively
            // returned nothing AND the api recorded a network failure. A user
            // with empty lists but no failure is a legitimate empty state.
            val networkFailure = api.lastFailure.value
            val nothingLoaded = user == null && watching.isEmpty() && reading.isEmpty() && recommendations.isEmpty()

            _uiState.value = _uiState.value.copy(
                user = user,
                continueWatching = watching,
                continueReading = reading,
                recommendations = recommendations,
                airingSchedule = airing,
                isLoading = false,
                error = if (networkFailure != null && nothingLoaded) friendlyMessage(networkFailure) else null,
            )
        }
    }

    fun retryLoadHomeData() {
        loadHomeData()
    }

    private fun friendlyMessage(failure: AnilistFailure): String = when (failure) {
        is AnilistFailure.Network -> "Couldn't reach AniList. Check your connection."
        is AnilistFailure.Server -> "AniList is having issues (HTTP ${failure.httpStatus})."
        is AnilistFailure.Other -> "Something went wrong loading your home feed."
    }

    /**
     * Lightweight refresh — only re-fetches AniList lists + user stats.
     * Skips recommendations (they don't change often).
     */
    private fun refreshAniListSections() {
        viewModelScope.launch {
            val userDeferred = async { repository.getUserData() }
            val watchingDeferred = async { repository.getUserAnimeList("CURRENT") + repository.getUserAnimeList("REPEATING") }
            val readingDeferred = async { repository.getUserMangaList("CURRENT") + repository.getUserMangaList("REPEATING") }

            val watching = watchingDeferred.await()
            val now = System.currentTimeMillis()
            val airing = watching
                .filter { it.nextAiringEpisodeTime != null && it.nextAiringEpisodeTime > now }
                .sortedBy { it.nextAiringEpisodeTime }

            _uiState.value = _uiState.value.copy(
                user = userDeferred.await(),
                continueWatching = watching,
                continueReading = readingDeferred.await(),
                airingSchedule = airing,
            )
        }
    }

    private fun observeLocalStats() {
        viewModelScope.launch {
            watchHistoryDao.getEpisodesWatchedCount().collect { count ->
                _uiState.value = _uiState.value.copy(localEpisodesWatched = count)
            }
        }
        viewModelScope.launch {
            readingHistoryDao.getChaptersReadCount().collect { count ->
                _uiState.value = _uiState.value.copy(localChaptersRead = count)
            }
        }
    }

    private fun observeReadingHistory() {
        viewModelScope.launch {
            readingHistoryDao.getRecent(10).collect { history ->
                _uiState.value = _uiState.value.copy(readingHistory = history)
            }
        }
    }

    private fun observeWatchHistory() {
        viewModelScope.launch {
            watchHistoryDao.getInProgress(10).collect { inProgress ->
                _uiState.value = _uiState.value.copy(continueWatchingLocal = inProgress)
            }
        }
        viewModelScope.launch {
            watchHistoryDao.getRecent(10).collect { recent ->
                _uiState.value = _uiState.value.copy(watchHistory = recent)
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
    val readingHistory: List<ReadingHistoryEntity> = emptyList(),
    val continueWatchingLocal: List<WatchHistoryEntity> = emptyList(),
    val watchHistory: List<WatchHistoryEntity> = emptyList(),
    val localEpisodesWatched: Int = 0,
    val localChaptersRead: Int = 0,
    val activityByDay: Map<LocalDate, Int> = emptyMap(),
    val activitiesByDay: Map<LocalDate, List<ani.saikou.components.DayActivity>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
