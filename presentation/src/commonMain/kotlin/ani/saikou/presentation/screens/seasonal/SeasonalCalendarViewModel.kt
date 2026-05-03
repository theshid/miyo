package ani.saikou.presentation.screens.seasonal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.AiringEntry
import ani.saikou.domain.model.Media
import ani.saikou.domain.usecase.anilist.GetAiringRangeUseCase
import ani.saikou.domain.usecase.anilist.GetSeasonalAnimeUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class SeasonalCalendarViewModel(
    private val getSeasonalAnime: GetSeasonalAnimeUseCase,
    private val getAiringRange: GetAiringRangeUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SeasonalUiState())
    val uiState: StateFlow<SeasonalUiState> = _uiState

    private var page = 1
    private var isLoadingMore = false

    init {
        // Determine current season + year
        val today =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        _uiState.update {
            it.copy(
                selectedSeason = monthToSeason(today.month),
                selectedYear = today.year,
            )
        }
        load()
    }

    fun selectSeason(season: String) {
        if (season == _uiState.value.selectedSeason) return
        _uiState.update { it.copy(selectedSeason = season) }
        reload()
    }

    fun changeYear(delta: Int) {
        val newYear = _uiState.value.selectedYear + delta
        if (newYear < MIN_YEAR || newYear > MAX_YEAR) return
        _uiState.update { it.copy(selectedYear = newYear) }
        reload()
    }

    fun selectTab(tab: CalendarTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadMore() {
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            page++
            val state = _uiState.value
            val more = getSeasonalAnime(state.selectedSeason, state.selectedYear, page)
            _uiState.update { it.copy(seasonalAnime = it.seasonalAnime + more) }
            isLoadingMore = false
        }
    }

    private fun reload() {
        page = 1
        load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val state = _uiState.value
            val seasonalDeferred =
                async { getSeasonalAnime(state.selectedSeason, state.selectedYear, 1) }
            val (start, end) = weekRangeForSeason(state.selectedSeason, state.selectedYear)
            val scheduleDeferred = async { getAiringRange(start, end) }

            val seasonal = seasonalDeferred.await()
            val schedule = scheduleDeferred.await()

            // Group schedule by day of week (formatted date label)
            val grouped = schedule.groupBy { dayLabel(it.airingAt) }

            _uiState.update {
                it.copy(
                    seasonalAnime = seasonal,
                    weeklySchedule = grouped,
                    weekDayLabels = weekDaysForSeason(state.selectedSeason, state.selectedYear),
                    isLoading = false,
                )
            }
        }
    }

    companion object {
        val SEASONS = listOf("WINTER", "SPRING", "SUMMER", "FALL")
        private const val MIN_YEAR = 1970
        private const val MAX_YEAR = 2030

        fun monthToSeason(month: Month): String =
            when (month) {
                Month.JANUARY, Month.FEBRUARY, Month.MARCH -> "WINTER"
                Month.APRIL, Month.MAY, Month.JUNE -> "SPRING"
                Month.JULY, Month.AUGUST, Month.SEPTEMBER -> "SUMMER"
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
         * Returns a Mon–Sun week range (epoch seconds) for the selected
         * season. Current season → current week; past/future season →
         * first full week starting on or after the season's first day.
         */
        fun weekRangeForSeason(
            season: String,
            year: Int,
        ): Pair<Long, Long> {
            val tz = TimeZone.UTC
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(tz)
                    .date
            val currentSeason = monthToSeason(today.month)

            val anchor =
                if (season == currentSeason && year == today.year) {
                    today
                } else {
                    val firstMonth =
                        when (season) {
                            "WINTER" -> Month.JANUARY
                            "SPRING" -> Month.APRIL
                            "SUMMER" -> Month.JULY
                            "FALL" -> Month.OCTOBER
                            else -> Month.JANUARY
                        }
                    LocalDate(year, firstMonth, 1)
                }

            // Roll back to Monday of that week. DayOfWeek.ordinal: Monday=0..Sunday=6.
            val monday = anchor.minus(anchor.dayOfWeek.ordinal, DateTimeUnit.DAY)
            val sunday = monday.plus(7, DateTimeUnit.DAY)
            val startInstant = monday.atStartOfDayIn(tz)
            val endInstant = sunday.atStartOfDayIn(tz)
            return startInstant.epochSeconds to endInstant.epochSeconds
        }

        /** Format like "Monday, Apr 14" — used both for grouping and display. */
        fun dayLabel(epochSeconds: Long): String {
            val date = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.currentSystemDefault()).date
            return "${dayName(date.dayOfWeek)}, ${monthShort(date.month)} ${date.dayOfMonth}"
        }

        fun weekDaysForSeason(
            season: String,
            year: Int,
        ): List<String> {
            val (startEpoch, _) = weekRangeForSeason(season, year)
            val tz = TimeZone.UTC
            val mondayDate = Instant.fromEpochSeconds(startEpoch).toLocalDateTime(tz).date
            return (0..6).map { offset ->
                val day = mondayDate.plus(offset, DateTimeUnit.DAY)
                "${dayName(day.dayOfWeek)}, ${monthShort(day.month)} ${day.dayOfMonth}"
            }
        }

        private fun dayName(day: DayOfWeek): String =
            when (day) {
                DayOfWeek.MONDAY -> "Monday"
                DayOfWeek.TUESDAY -> "Tuesday"
                DayOfWeek.WEDNESDAY -> "Wednesday"
                DayOfWeek.THURSDAY -> "Thursday"
                DayOfWeek.FRIDAY -> "Friday"
                DayOfWeek.SATURDAY -> "Saturday"
                DayOfWeek.SUNDAY -> "Sunday"
                else -> "Unknown"
            }

        private fun monthShort(month: Month): String =
            when (month) {
                Month.JANUARY -> "Jan"
                Month.FEBRUARY -> "Feb"
                Month.MARCH -> "Mar"
                Month.APRIL -> "Apr"
                Month.MAY -> "May"
                Month.JUNE -> "Jun"
                Month.JULY -> "Jul"
                Month.AUGUST -> "Aug"
                Month.SEPTEMBER -> "Sep"
                Month.OCTOBER -> "Oct"
                Month.NOVEMBER -> "Nov"
                Month.DECEMBER -> "Dec"
                else -> month.name.take(3)
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
