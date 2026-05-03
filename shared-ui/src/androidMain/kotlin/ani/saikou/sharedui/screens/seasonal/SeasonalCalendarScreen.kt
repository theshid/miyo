package ani.saikou.sharedui.screens.seasonal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ani.saikou.domain.model.AiringEntry
import ani.saikou.domain.model.Media
import ani.saikou.presentation.screens.seasonal.CalendarTab
import ani.saikou.presentation.screens.seasonal.SeasonalCalendarViewModel
import ani.saikou.sharedui.components.GenreChip
import ani.saikou.sharedui.components.SectionHeader
import ani.saikou.sharedui.theme.Background
import ani.saikou.sharedui.theme.OnSurface
import ani.saikou.sharedui.theme.OnSurfaceVariant
import ani.saikou.sharedui.theme.Primary
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SeasonalCalendarScreen(
    onNavigateToMedia: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: SeasonalCalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background),
    ) {
        // ── Top bar ──────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 4.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnSurface,
                )
            }
            Text(
                text = "Seasonal Calendar",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
                fontWeight = FontWeight.Bold,
            )
        }

        // ── Season + Year selector ───────────────────────────
        SeasonYearSelector(
            season = state.selectedSeason,
            year = state.selectedYear,
            onSeasonChange = viewModel::selectSeason,
            onYearChange = viewModel::changeYear,
        )

        // ── Tabs: Seasonal / Schedule ────────────────────────
        val tabs = listOf("This Season", "Weekly Schedule")
        val selectedIndex = if (state.selectedTab == CalendarTab.SEASONAL) 0 else 1

        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = Background,
            contentColor = Primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = Primary,
                )
            },
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = {
                        viewModel.selectTab(
                            if (index == 0) CalendarTab.SEASONAL else CalendarTab.SCHEDULE,
                        )
                    },
                    text = {
                        Text(
                            text = title,
                            color = if (index == selectedIndex) Primary else OnSurfaceVariant,
                        )
                    },
                )
            }
        }

        // ── Content ──────────────────────────────────────────
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ani.saikou.sharedui.components
                    .DiscoveryShimmer()
            }
        } else {
            when (state.selectedTab) {
                CalendarTab.SEASONAL ->
                    SeasonalGrid(
                        anime = state.seasonalAnime,
                        onItemClick = onNavigateToMedia,
                        onLoadMore = viewModel::loadMore,
                    )
                CalendarTab.SCHEDULE ->
                    WeeklySchedule(
                        schedule = state.weeklySchedule,
                        weekDayLabels = state.weekDayLabels,
                        onItemClick = onNavigateToMedia,
                    )
            }
        }
    }
}

@Composable
private fun SeasonYearSelector(
    season: String,
    year: Int,
    onSeasonChange: (String) -> Unit,
    onYearChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Year row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = { onYearChange(-1) }) {
                Icon(Icons.Default.ChevronLeft, "Previous year", tint = OnSurface)
            }
            Text(
                text = "$year",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurface,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { onYearChange(1) }) {
                Icon(Icons.Default.ChevronRight, "Next year", tint = OnSurface)
            }
        }

        // Season chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeasonalCalendarViewModel.SEASONS.forEach { s ->
                GenreChip(
                    text = SeasonalCalendarViewModel.seasonLabel(s).uppercase(),
                    selected = s == season,
                    onClick = { onSeasonChange(s) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ── Seasonal Grid Tab ────────────────────────────────────────

@Composable
private fun SeasonalGrid(
    anime: List<Media>,
    onItemClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (anime.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No anime found for this season", color = OnSurfaceVariant)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(
            items = anime,
            key = { it.id },
        ) { media ->
            SeasonalAnimeCard(
                media = media,
                onClick = { onItemClick(media.id) },
            )
        }

        // Trigger load more near the end
        if (anime.size >= 20) {
            item {
                Spacer(modifier = Modifier.height(1.dp))
                onLoadMore()
            }
        }
    }
}

@Composable
private fun SeasonalAnimeCard(
    media: Media,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Poster
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = media.cover,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Score badge
            media.meanScore?.takeIf { it > 0 }?.let { score ->
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp),
                            ).padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "★ ${score / 10.0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                    )
                }
            }

            // Status badge (airing, finished, etc.)
            if (media.isOngoing) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(Primary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "AIRING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Title
        Text(
            text = media.displayTitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )

        // Episode count
        val epCount = media.totalEpisodes
        val epText =
            when {
                epCount != null && epCount > 0 -> "$epCount eps"
                media.isOngoing -> "Airing"
                else -> null
            }
        if (epText != null) {
            Text(
                text = epText,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
            )
        }
    }
}

// ── Weekly Schedule Tab ──────────────────────────────────────

@Composable
private fun WeeklySchedule(
    schedule: Map<String, List<AiringEntry>>,
    weekDayLabels: List<String>,
    onItemClick: (Int) -> Unit,
) {
    if (schedule.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No airing schedule found this week", color = OnSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        weekDayLabels.forEach { day ->
            val entries = schedule[day]
            if (!entries.isNullOrEmpty()) {
                item(key = day) {
                    DaySection(
                        day = day,
                        entries = entries,
                        onItemClick = onItemClick,
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun DaySection(
    day: String,
    entries: List<AiringEntry>,
    onItemClick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = day,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = entries,
                key = { it.media.id },
            ) { entry ->
                ScheduleCard(
                    entry = entry,
                    onClick = { onItemClick(entry.media.id) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    entry: AiringEntry,
    onClick: () -> Unit,
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(entry.airingAt * 1000))

    Column(
        modifier =
            Modifier
                .width(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Poster
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = entry.media.cover,
                contentDescription = entry.media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Episode + time badge
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(
                            Primary.copy(alpha = 0.9f),
                            RoundedCornerShape(4.dp),
                        ).padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "Ep ${entry.episode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Title
        Text(
            text = entry.media.displayTitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )

        // Airing time
        Text(
            text = "\uD83D\uDD53 Airs at $timeStr",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
        )
    }
}
