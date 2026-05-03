package ani.saikou.presentation.screens.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.AiringScheduleItem
import ani.saikou.domain.model.NewsItem
import ani.saikou.domain.usecase.news.GetAiringScheduleUseCase
import ani.saikou.domain.usecase.news.GetLatestNewsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class NewsFeedViewModel(
    private val getLatestNews: GetLatestNewsUseCase,
    private val getAiringSchedule: GetAiringScheduleUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NewsFeedUiState())
    val uiState: StateFlow<NewsFeedUiState> = _uiState

    init {
        selectDay(currentWeekdayIndex())
        loadNews()
    }

    fun selectDay(index: Int) {
        _uiState.update { it.copy(selectedDayIndex = index, scheduleLoading = true) }
        viewModelScope.launch {
            val schedule = getAiringSchedule(DAY_KEYS[index])
            _uiState.update { it.copy(schedule = schedule, scheduleLoading = false) }
        }
    }

    fun loadNews() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val news = getLatestNews()
            _uiState.update { it.copy(news = news, isLoading = false) }
        }
    }

    fun setFilter(filter: String?) {
        _uiState.update { it.copy(sourceFilter = filter) }
    }

    /** Monday = 0..Sunday = 6. KMP-portable via kotlinx-datetime. */
    private fun currentWeekdayIndex(): Int =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .dayOfWeek.ordinal

    companion object {
        private val DAY_KEYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
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
) {
    /** Filtered view of [news] — null filter means all sources. */
    val filteredNews: List<NewsItem>
        get() = if (sourceFilter == null) news else news.filter { it.source == sourceFilter }
}
