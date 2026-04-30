package ani.saikou.screens.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.remote.news.ANNNewsSource
import ani.saikou.data.remote.news.JikanNewsSource
import ani.saikou.data.remote.news.RedditNewsSource
import ani.saikou.domain.model.AiringScheduleItem
import ani.saikou.domain.model.NewsItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NewsFeedViewModel : ViewModel() {
    private val jikan = JikanNewsSource()
    private val reddit = RedditNewsSource()
    private val ann = ANNNewsSource()

    private val _uiState = MutableStateFlow(NewsFeedUiState())
    val uiState: StateFlow<NewsFeedUiState> = _uiState

    private val days = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    private val dayLabels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    init {
        val today =
            java.util.Calendar
                .getInstance()
                .get(java.util.Calendar.DAY_OF_WEEK)
        // Calendar.MONDAY=2..SUNDAY=1 → map to 0-6
        val dayIndex = if (today == 1) 6 else today - 2
        selectDay(dayIndex)
        loadNews()
    }

    fun selectDay(index: Int) {
        _uiState.value = _uiState.value.copy(selectedDayIndex = index, scheduleLoading = true)
        viewModelScope.launch {
            val schedule = jikan.getSchedule(days[index])
            _uiState.value =
                _uiState.value.copy(
                    schedule = schedule,
                    scheduleLoading = false,
                )
        }
    }

    fun loadNews() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val results =
                listOf(
                    async { jikan.getLatestNews() },
                    async { reddit.getLatestNews() },
                    async { ann.getLatestNews() },
                ).awaitAll().flatten()

            // Deduplicate by title similarity and sort by date
            val deduped =
                results
                    .distinctBy { it.title.lowercase().take(50) }
                    .sortedByDescending { it.date }

            _uiState.value =
                _uiState.value.copy(
                    news = deduped,
                    isLoading = false,
                )
        }
    }

    fun setFilter(filter: String?) {
        _uiState.value = _uiState.value.copy(sourceFilter = filter)
    }

    fun getFilteredNews(): List<NewsItem> {
        val state = _uiState.value
        return if (state.sourceFilter == null) {
            state.news
        } else {
            state.news.filter { it.source == state.sourceFilter }
        }
    }

    companion object {
        val DAY_LABELS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    }
}

data class NewsFeedUiState(
    val schedule: List<AiringScheduleItem> = emptyList(),
    val scheduleLoading: Boolean = true,
    val selectedDayIndex: Int = 0,
    val news: List<NewsItem> = emptyList(),
    val sourceFilter: String? = null,
    val isLoading: Boolean = true,
)
