package ani.saikou.domain.model

import kotlinx.datetime.LocalDate

/**
 * Year-to-date snapshot of the user's activity, bucketed by day in the
 * device's local timezone. Backs the home-screen heatmap.
 *
 * - [countsByDay]: total event count per day (used for the heat-color tile).
 * - [activitiesByDay]: per-day list of *unique* watched episodes / read
 *   chapters — drops session events and pre-v8 rows missing media context;
 *   de-duplicated by `(mediaId, kind, number)` so replays don't inflate.
 */
data class ActivityCalendarSnapshot(
    val countsByDay: Map<LocalDate, Int>,
    val activitiesByDay: Map<LocalDate, List<DayActivity>>,
)
