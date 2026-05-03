package ani.saikou.domain.usecase.activity

import ani.saikou.domain.model.ActivityCalendarSnapshot
import ani.saikou.domain.model.ActivityEvent
import ani.saikou.domain.model.DayActivity
import ani.saikou.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Observes the user's activity since Jan 1 of the device-local year and
 * buckets it into the per-day shape the home heatmap consumes.
 *
 * The lower bound is conservative — fetching the whole year up front
 * lets the heatmap UI page through months without a second round-trip.
 * Bucket key timezone is the device's, since that's what the user reads
 * the calendar in.
 */
class ObserveActivityCalendarUseCase(
    private val repository: ActivityRepository,
) {
    operator fun invoke(): Flow<ActivityCalendarSnapshot> {
        val zone = TimeZone.currentSystemDefault()
        val today =
            Clock.System
                .now()
                .toLocalDateTime(zone)
                .date
        val yearStart = LocalDate(today.year, 1, 1)
        val yearStartMs = yearStart.atStartOfDayIn(zone).toEpochMilliseconds()
        return repository
            .observeActivitySince(yearStartMs)
            .map { events -> assemble(events, zone) }
    }

    private fun assemble(
        events: List<ActivityEvent>,
        zone: TimeZone,
    ): ActivityCalendarSnapshot {
        val byDay =
            events.groupBy { event ->
                Instant
                    .fromEpochMilliseconds(event.timestampMs)
                    .toLocalDateTime(zone)
                    .date
            }
        val counts = byDay.mapValues { (_, list) -> list.size }

        // Build the per-day list of "what you actually did" for the heatmap menu.
        // Drop session events and rows missing media context (pre-v8 schema).
        val activities =
            byDay.mapValues { (_, list) ->
                list
                    .mapNotNull { event ->
                        val title = event.mediaTitle ?: return@mapNotNull null
                        val mediaId = event.mediaId ?: return@mapNotNull null
                        when (event.type) {
                            "watch" ->
                                DayActivity(
                                    mediaId = mediaId,
                                    title = title,
                                    coverUrl = event.coverUrl,
                                    kind = DayActivity.Kind.WATCHED,
                                    number = event.episodeNumber ?: 0,
                                    timestampMs = event.timestampMs,
                                )
                            "read" ->
                                DayActivity(
                                    mediaId = mediaId,
                                    title = title,
                                    coverUrl = event.coverUrl,
                                    kind = DayActivity.Kind.READ,
                                    number = event.chapterNumber ?: 0,
                                    timestampMs = event.timestampMs,
                                )
                            else -> null
                        }
                    }
                    // De-duplicate: a single chapter/episode can fire multiple
                    // events as the user swaps pages or replays; we want one
                    // row per (media, kind, number).
                    .distinctBy { Triple(it.mediaId, it.kind, it.number) }
                    .sortedByDescending { it.timestampMs }
            }

        return ActivityCalendarSnapshot(countsByDay = counts, activitiesByDay = activities)
    }
}
