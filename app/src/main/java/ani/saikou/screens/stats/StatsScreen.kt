package ani.saikou.screens.stats

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.components.GlassCard
import ani.saikou.domain.model.GenreStat
import ani.saikou.domain.model.ScoreStat
import ani.saikou.domain.model.StatusStat
import ani.saikou.domain.model.UserStats
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary
import ani.saikou.ui.theme.SurfaceContainerHigh
import ani.saikou.ui.theme.Tertiary
import ani.saikou.util.ShareCardGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    .padding(top = 48.dp, start = 4.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
            }
            Text(
                text = "Your Stats",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (state.stats != null) {
                IconButton(onClick = {
                    scope.launch(Dispatchers.Default) {
                        shareStats(context, state.stats!!)
                    }
                }) {
                    Icon(Icons.Default.Share, "Share stats", tint = Primary)
                }
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ani.saikou.components.DiscoveryShimmer()
            }
            return
        }

        val stats = state.stats
        if (stats == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Could not load stats", color = OnSurfaceVariant)
            }
            return
        }

        // ── Tabs ─────────────────────────────────────────────
        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("Anime", "Manga")

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Background,
            contentColor = Primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Primary,
                )
            },
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = index == selectedTab,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (index == selectedTab) Primary else OnSurfaceVariant,
                        )
                    },
                )
            }
        }

        when (selectedTab) {
            0 -> AnimeStatsTab(stats)
            1 -> MangaStatsTab(stats)
        }
    }
}

// ── Anime Tab ────────────────────────────────────────────────

@Composable
private fun AnimeStatsTab(stats: UserStats) {
    val anime = stats.anime

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Overview cards
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OverviewCard(
                    value = "${anime.count}",
                    label = "Total Anime",
                    color = Primary,
                    modifier = Modifier.weight(1f),
                )
                OverviewCard(
                    value = "${anime.episodesWatched}",
                    label = "Episodes",
                    color = Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OverviewCard(
                    value = formatWatchTime(anime.minutesWatched),
                    label = "Watch Time",
                    color = Tertiary,
                    modifier = Modifier.weight(1f),
                )
                OverviewCard(
                    value = "%.1f".format(anime.meanScore),
                    label = "Mean Score",
                    color = Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Status breakdown
        if (anime.statuses.isNotEmpty()) {
            item { SectionTitle("Status Breakdown") }
            item { StatusBreakdown(anime.statuses) }
        }

        // Genre distribution
        if (anime.genres.isNotEmpty()) {
            item { SectionTitle("Top Genres") }
            item { GenreChart(anime.genres) }
        }

        // Score distribution
        if (anime.scores.isNotEmpty()) {
            item { SectionTitle("Score Distribution") }
            item { ScoreHistogram(anime.scores) }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ── Manga Tab ────────────────────────────────────────────────

@Composable
private fun MangaStatsTab(stats: UserStats) {
    val manga = stats.manga

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OverviewCard(
                    value = "${manga.count}",
                    label = "Total Manga",
                    color = Primary,
                    modifier = Modifier.weight(1f),
                )
                OverviewCard(
                    value = "${manga.chaptersRead}",
                    label = "Chapters",
                    color = Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OverviewCard(
                    value = "${manga.volumesRead}",
                    label = "Volumes",
                    color = Tertiary,
                    modifier = Modifier.weight(1f),
                )
                OverviewCard(
                    value = "%.1f".format(manga.meanScore),
                    label = "Mean Score",
                    color = Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (manga.statuses.isNotEmpty()) {
            item { SectionTitle("Status Breakdown") }
            item { StatusBreakdown(manga.statuses) }
        }

        if (manga.genres.isNotEmpty()) {
            item { SectionTitle("Top Genres") }
            item { GenreChart(manga.genres) }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ── Reusable Components ──────────────────────────────────────

@Composable
private fun OverviewCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = color,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = OnSurface,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun StatusBreakdown(statuses: List<StatusStat>) {
    val total = statuses.sumOf { it.count }.coerceAtLeast(1)
    val colors =
        listOf(
            Color(0xFF4CAF50), // Watching/Reading
            Color(0xFF2196F3), // Completed
            Color(0xFFFF9800), // Paused
            Color(0xFFE91E63), // Dropped
            Color(0xFF9C27B0), // Planning
        )

    GlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Stacked bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
            ) {
                statuses.forEachIndexed { index, status ->
                    val fraction = status.count.toFloat() / total
                    if (fraction > 0f) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(fraction)
                                    .fillMaxHeight()
                                    .background(colors.getOrElse(index) { OnSurfaceVariant }),
                        )
                    }
                }
            }

            // Legend
            statuses.forEachIndexed { index, status ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors.getOrElse(index) { OnSurfaceVariant }),
                    )
                    Text(
                        text = formatStatus(status.status),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${status.count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreChart(genres: List<GenreStat>) {
    val maxCount = genres.maxOfOrNull { it.count } ?: 1

    GlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.take(8).forEach { genre ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = genre.genre,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurface,
                        modifier = Modifier.width(80.dp),
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(16.dp),
                    ) {
                        // Track
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceContainerHigh),
                        )
                        // Fill
                        val fraction = genre.count.toFloat() / maxCount
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Primary),
                        )
                    }
                    Text(
                        text = "${genre.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreHistogram(scores: List<ScoreStat>) {
    val maxCount = scores.maxOfOrNull { it.count }?.toFloat()?.coerceAtLeast(1f) ?: 1f

    GlassCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
            ) {
                val barWidth = size.width / 10f
                val gap = 4.dp.toPx()

                // Draw bars for scores 1-10
                for (s in 1..10) {
                    val count = scores.find { it.score == s * 10 }?.count ?: 0
                    val fraction = count.toFloat() / maxCount
                    val barH = fraction * size.height * 0.85f
                    val x = (s - 1) * barWidth + gap / 2

                    drawRoundRect(
                        color = Primary,
                        topLeft = Offset(x, size.height - barH),
                        size = Size(barWidth - gap, barH),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }

            // Labels
            Row(modifier = Modifier.fillMaxWidth()) {
                for (s in 1..10) {
                    Text(
                        text = "$s",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────

private fun formatWatchTime(minutes: Int): String {
    val days = minutes / 1440
    val hours = (minutes % 1440) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

private fun formatStatus(status: String): String =
    when (status) {
        "CURRENT" -> "Watching"
        "PLANNING" -> "Planning"
        "COMPLETED" -> "Completed"
        "DROPPED" -> "Dropped"
        "PAUSED" -> "Paused"
        "REPEATING" -> "Rewatching"
        else -> status.lowercase().replaceFirstChar { it.uppercase() }
    }

private suspend fun shareStats(
    context: Context,
    stats: UserStats,
) {
    val bitmap =
        ShareCardGenerator.generateStatsCard(
            context = context,
            input =
                ShareCardGenerator.StatsCardInput(
                    userName = stats.userName,
                    avatarUrl = stats.avatar,
                    episodesWatched = stats.anime.episodesWatched,
                    minutesWatched = stats.anime.minutesWatched,
                    animeCount = stats.anime.count,
                    meanScore = stats.anime.meanScore,
                    topGenres =
                        stats.anime.genres
                            .take(5)
                            .map { it.genre },
                    chaptersRead = stats.manga.chaptersRead,
                    mangaCount = stats.manga.count,
                ),
        )
    val uri = ShareCardGenerator.saveToCacheAndGetUri(context, bitmap, "miyo_stats.png")
    ShareCardGenerator.shareImage(context, uri, "My anime stats on Miyo")
}
