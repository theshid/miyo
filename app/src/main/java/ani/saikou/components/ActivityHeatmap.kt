package ani.saikou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary
import ani.saikou.ui.theme.SurfaceContainer
import ani.saikou.ui.theme.SurfaceContainerHigh
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class AiringInfo(
    val mediaId: Int,
    val title: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val airingTimeMs: Long,
)

@Composable
fun ActivityHeatmap(
    countsByDay: Map<LocalDate, Int>,
    airingsByDay: Map<LocalDate, List<AiringInfo>> = emptyMap(),
    onAiringClick: (mediaId: Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val firstOfMonth = today.withDayOfMonth(1)
    val lastOfMonth = today.withDayOfMonth(today.lengthOfMonth())
    val gridStart = firstOfMonth.with(DayOfWeek.MONDAY)
    val gridEnd = lastOfMonth.with(DayOfWeek.SUNDAY)
    val totalDays = ChronoUnit.DAYS.between(gridStart, gridEnd).toInt() + 1
    val rows = totalDays / 7

    val monthLabel = firstOfMonth.month
        .getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + firstOfMonth.year

    GlassCard(modifier = modifier, contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                )
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                )
            }

            // Weekday header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                        )
                    }
                }
            }

            // Calendar grid — rows = weeks, cols = Mon..Sun
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (col in 0 until 7) {
                            val date = gridStart.plusDays((row * 7 + col).toLong())
                            HeatmapCell(
                                date = date,
                                today = today,
                                count = if (date.month == today.month && date.year == today.year && !date.isAfter(today))
                                    countsByDay[date] ?: 0 else 0,
                                airings = if (date.month == today.month && date.year == today.year)
                                    airingsByDay[date].orEmpty() else emptyList(),
                                onAiringClick = onAiringClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (airingsByDay.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .border(1.dp, Secondary, CircleShape),
                        )
                        Text(
                            "Airing",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                        )
                    }
                } else {
                    Box(modifier = Modifier.size(0.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Less", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    listOf(0, 1, 3, 6, 11).forEach { sample ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cellColor(sample, inMonth = true, isFuture = false))
                        )
                    }
                    Text("More", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    date: LocalDate,
    today: LocalDate,
    count: Int,
    airings: List<AiringInfo>,
    onAiringClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inMonth = date.month == today.month && date.year == today.year
    val isFuture = date.isAfter(today)
    val isToday = date == today
    var menuOpen by remember { mutableStateOf(false) }

    val cover = airings.firstOrNull()?.coverUrl
    val cellMod = modifier
        .clip(RoundedCornerShape(4.dp))
        .background(cellColor(count, inMonth, isFuture))
        .let { base ->
            if (isToday) base.border(1.5.dp, Primary, RoundedCornerShape(4.dp))
            else if (cover != null) base.border(1.dp, Secondary, RoundedCornerShape(4.dp))
            else base
        }
        .let { base ->
            if (airings.isNotEmpty()) base.clickable { menuOpen = true } else base
        }

    Box(modifier = cellMod) {
        if (cover != null) {
            coil.compose.AsyncImage(
                model = cover,
                contentDescription = "Episode airing",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(4.dp)),
            )
            // Dark scrim so the day number stays legible over any cover.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
            )
        }
        if (inMonth) {
            Text(
                text = "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (cover != null) Color.White else dayTextColor(count, isFuture),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (airings.isNotEmpty()) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = SurfaceContainerHigh,
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                airings.forEach { airing ->
                    DropdownMenuItem(
                        text = {
                            Column(modifier = Modifier.width(220.dp)) {
                                Text(
                                    text = airing.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "Ep ${airing.episodeNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Secondary,
                                    )
                                    Text(
                                        text = "·",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceVariant,
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Secondary,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = formatAiringTime(airing.airingTimeMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Secondary,
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            coil.compose.AsyncImage(
                                model = airing.coverUrl,
                                contentDescription = airing.title,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 32.dp, height = 44.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onAiringClick(airing.mediaId)
                        },
                    )
                }
            }
        }
    }
}

private fun formatAiringTime(timeMs: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(timeMs).atZone(zone).toLocalTime()
    return time.format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun cellColor(count: Int, inMonth: Boolean, isFuture: Boolean): Color {
    if (!inMonth) return Color.Transparent
    if (isFuture) return SurfaceContainer.copy(alpha = 0.35f)
    return when {
        count <= 0 -> SurfaceContainer
        count <= 2 -> Primary.copy(alpha = 0.25f)
        count <= 5 -> Primary.copy(alpha = 0.5f)
        count <= 10 -> Primary.copy(alpha = 0.75f)
        else -> Primary
    }
}

private fun dayTextColor(count: Int, isFuture: Boolean): Color {
    if (isFuture) return OnSurfaceVariant.copy(alpha = 0.35f)
    return if (count > 5) Color.White else OnSurfaceVariant
}
