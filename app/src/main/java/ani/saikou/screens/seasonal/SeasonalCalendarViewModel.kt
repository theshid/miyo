package ani.saikou.screens.seasonal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.AiringEntry
import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AnilistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class SeasonalCalendarViewModel(
    private val repository: AnilistRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SeasonalUiState())
    val uiState: StateFlow<SeasonalUiState> = _uiState

    private var page = 1
    private var isLoadingMore = false

    init {
        // Determine current season + year
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) // 0-based
        val year = cal.get(Calendar.YEAR)
        val season = monthToSeason(month)
        _uiState.value =
            _uiState.value.copy(
                selectedSeason = season,
                selectedYear = year,
            )
        load()
    }

    fun selectSeason(season: String) {
        if (season == _uiState.value.selectedSeason) return
        _uiState.value = _uiState.value.copy(selectedSeason = season)
        reload()
    }

    fun changeYear(delta: Int) {
        val newYear = _uiState.value.selectedYear + delta
        if (newYear < 1970 || newYear > 2030) return
        _uiState.value = _uiState.value.copy(selectedYear = newYear)
        reload()
    }

    fun selectTab(tab: CalendarTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun loadMore() {
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            page++
            val more =
                repository.getSeasonalAnime(
                    _uiState.value.selectedSeason,
                    _uiState.value.selectedYear,
                    page,
                )
            _uiState.value =
                _uiState.value.copy(
                    seasonalAnime = _uiState.value.seasonalAnime + more,
                )
            isLoadingMore = false
        }
    }

    private fun reload() {
        page = 1
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val state = _uiState.value
            val seasonalDeferred =
                async {
                    repository.getSeasonalAnime(state.selectedSeason, state.selectedYear, 1)
                }
            val scheduleDeferred =
                async {
                    val (start, end) = weekRangeForSeason(state.selectedSeason, state.selectedYear)
                    repository.getAiringSchedule(start, end)
                }

            val seasonal = seasonalDeferred.await()
            val schedule = scheduleDeferred.await()

            // Group schedule by day of week
            val grouped =
                schedule.groupBy { entry ->
                    dayOfWeek(entry.airingAt)
                }

            _uiState.value =
                state.copy(
                    seasonalAnime = seasonal,
                    weeklySchedule = grouped,
                    weekDayLabels = weekDaysForSeason(state.selectedSeason, state.selectedYear),
                    isLoading = false,
                )
        }
    }

    companion object {
        val SEASONS = listOf("WINTER", "SPRING", "SUMMER", "FALL")

        fun monthToSeason(month: Int): String =
            when (month) {
                0, 1, 2 -> "WINTER"
                3, 4, 5 -> "SPRING"
                6, 7, 8 -> "SUMMER"
                else -> "FALL"
            }

        fun seasonLabel(season: String): String =
            when (season) {
                "WINTER" -> "Winter"
                "SPRING" -> "Spring"
                "SUMMER" -> "Summer"
                "FALL" -> "Fall"
                else -> season
            }

        /**
         * Returns a Mon–Sun week range (epoch seconds) for the selected season.
         * - Current season → current week
         * - Past/future season → first full week of that season
         */
        fun weekRangeForSeason(
            season: String,
            year: Int,
        ): Pair<Long, Long> {
            val now = Calendar.getInstance()
            val currentSeason = monthToSeason(now.get(Calendar.MONTH))
            val currentYear = now.get(Calendar.YEAR)

            val cal =
                if (season == currentSeason && year == currentYear) {
                    // Current season → use this week
                    Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                } else {
                    // Different season → use the first Monday of that season
                    val startMonth =
                        when (season) {
                            "WINTER" -> Calendar.JANUARY
                            "SPRING" -> Calendar.APRIL
                            "SUMMER" -> Calendar.JULY
                            "FALL" -> Calendar.OCTOBER
                            else -> Calendar.JANUARY
                        }
                    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, startMonth)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                }

            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            // Roll back to Monday
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
            cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
            val start = cal.timeInMillis / 1000
            cal.add(Calendar.DAY_OF_YEAR, 7)
            val end = cal.timeInMillis / 1000
            return start to end
        }

        private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        /**
         * Returns a key like "Monday, Apr 14" for grouping + display.
         */
        fun dayOfWeek(epochSeconds: Long): String {
            val cal = Calendar.getInstance()
            cal.timeInMillis = epochSeconds * 1000
            val dayName =
                when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Monday"
                    Calendar.TUESDAY -> "Tuesday"
                    Calendar.WEDNESDAY -> "Wednesday"
                    Calendar.THURSDAY -> "Thursday"
                    Calendar.FRIDAY -> "Friday"
                    Calendar.SATURDAY -> "Saturday"
                    Calendar.SUNDAY -> "Sunday"
                    else -> "Unknown"
                }
            val dateStr = dateFormat.format(cal.time)
            return "$dayName, $dateStr"
        }

        /**
         * Returns ordered day labels (Mon–Sun) with dates for the given season/year.
         * e.g. ["Monday, Apr 14", "Tuesday, Apr 15", ...]
         */
        fun weekDaysForSeason(
            season: String,
            year: Int,
        ): List<String> {
            val (startEpoch, _) = weekRangeForSeason(season, year)
            val cal = Calendar.getInstance()
            cal.timeInMillis = startEpoch * 1000

            return (0..6).map { offset ->
                val dayCal = cal.clone() as Calendar
                dayCal.add(Calendar.DAY_OF_YEAR, offset)
                val dayName =
                    when (dayCal.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> "Monday"
                        Calendar.TUESDAY -> "Tuesday"
                        Calendar.WEDNESDAY -> "Wednesday"
                        Calendar.THURSDAY -> "Thursday"
                        Calendar.FRIDAY -> "Friday"
                        Calendar.SATURDAY -> "Saturday"
                        Calendar.SUNDAY -> "Sunday"
                        else -> "Unknown"
                    }
                "$dayName, ${dateFormat.format(dayCal.time)}"
            }
        }
    }
}

enum class CalendarTab { SEASONAL, SCHEDULE }

data class SeasonalUiState(
    val selectedSeason: String = "WINTER",
    val selectedYear: Int = 2025,
    val selectedTab: CalendarTab = CalendarTab.SEASONAL,
    val seasonalAnime: List<Media> = emptyList(),
    val weeklySchedule: Map<String, List<AiringEntry>> = emptyMap(),
    val weekDayLabels: List<String> = emptyList(),
    val isLoading: Boolean = true,
)
