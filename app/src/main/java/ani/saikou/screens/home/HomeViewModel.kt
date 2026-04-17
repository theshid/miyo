package ani.saikou.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.local.db.ReadingHistoryEntity
import ani.saikou.data.local.db.WatchHistoryEntity
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repository = AppModule.repository()
    private val readingHistoryDao = AppModule.readingHistoryDao()
    private val watchHistoryDao = AppModule.watchHistoryDao()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadHomeData()
        observeReadingHistory()
        observeWatchHistory()
        observeLocalStats()
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

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userDeferred = async { repository.getUserData() }
            val watchingDeferred = async { repository.getUserAnimeList("CURRENT") + repository.getUserAnimeList("REPEATING") }
            val readingDeferred = async { repository.getUserMangaList("CURRENT") + repository.getUserMangaList("REPEATING") }
            val recommendationsDeferred = async { repository.getRecommendations() }

            val watching = watchingDeferred.await()

            // Derive airing schedule: shows on user's CURRENT list that have a
            // future airing time, sorted soonest-first.
            val now = System.currentTimeMillis()
            val airing = watching
                .filter { it.nextAiringEpisodeTime != null && it.nextAiringEpisodeTime > now }
                .sortedBy { it.nextAiringEpisodeTime }

            _uiState.value = _uiState.value.copy(
                user = userDeferred.await(),
                continueWatching = watching,
                continueReading = readingDeferred.await(),
                recommendations = recommendationsDeferred.await(),
                airingSchedule = airing,
                isLoading = false,
            )
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
    val isLoading: Boolean = true,
)
