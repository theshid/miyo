package ani.saikou.screens.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.components.GlassCard
import ani.saikou.components.MediaPosterCard
import ani.saikou.components.PulseAvatar
import ani.saikou.components.SectionHeader
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary

@Composable
fun HomeScreen(
    onNavigateToAnimeList: () -> Unit,
    onNavigateToMangaList: () -> Unit,
    onNavigateToMedia: (Int) -> Unit,
    onNavigateToNews: () -> Unit = {},
    onNavigateToTorrent: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToReader: (mediaId: Int, chapterNum: Int) -> Unit = { _, _ -> },
    onNavigateToPlayer: (mediaId: Int, episodeNum: Int) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Refresh the list every time the screen comes back into view so changes
    // made in MediaDetail (add/remove/change status) appear immediately.
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadHomeData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out", color = OnSurface) },
            text = { Text("Are you sure you want to log out of AniList?", color = OnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Log out", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            },
            containerColor = ani.saikou.ui.theme.SurfaceContainerHigh,
        )
    }

    if (state.isLoading) {
        ani.saikou.components.HomeShimmer()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── User Header ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PulseAvatar(imageUrl = state.user?.avatar, size = 48.dp)
                Column {
                    Text(
                        text = "Hey, ${state.user?.name ?: "User"}",
                        style = MaterialTheme.typography.titleLarge,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row {
                IconButton(onClick = onNavigateToStats) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = "Stats",
                        tint = OnSurfaceVariant,
                    )
                }
                IconButton(onClick = onNavigateToNews) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "News",
                        tint = OnSurfaceVariant,
                    )
                }
                IconButton(onClick = { showLogoutDialog = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log out",
                        tint = OnSurfaceVariant,
                    )
                }
            }
        }

        // ── Stats Cards ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                value = "${state.localEpisodesWatched}",
                label = "Episodes Watched",
                accentColor = Secondary,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "${state.localChaptersRead}",
                label = "Chapters Read",
                accentColor = Primary,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Quick Action Cards ───────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionCard(
                title = "Anime List",
                icon = Icons.Outlined.PlayCircle,
                onClick = onNavigateToAnimeList,
                modifier = Modifier.weight(1f),
            )
            ActionCard(
                title = "Manga List",
                icon = Icons.Outlined.MenuBook,
                onClick = onNavigateToMangaList,
                modifier = Modifier.weight(1f),
            )
        }

        // ── News & Torrents ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionCard(
                title = "News",
                icon = Icons.Default.Notifications,
                onClick = onNavigateToNews,
                modifier = Modifier.weight(1f),
            )
            ActionCard(
                title = "Torrents",
                icon = Icons.Default.Download,
                onClick = onNavigateToTorrent,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Offline & Calendar ───────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionCard(
                title = "Offline",
                icon = Icons.Default.DownloadDone,
                onClick = onNavigateToDownloads,
                modifier = Modifier.weight(1f),
            )
            ActionCard(
                title = "Calendar",
                icon = Icons.Default.CalendarMonth,
                onClick = onNavigateToCalendar,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Airing Soon ─────────────────────────────────────
        if (state.airingSchedule.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = "Airing Soon",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = state.airingSchedule,
                        key = { "airing_${it.id}" },
                    ) { media ->
                        AiringCard(
                            title = media.displayTitle,
                            coverUrl = media.cover,
                            episodeNumber = (media.nextAiringEpisode ?: 0) + 1,
                            airingAtMs = media.nextAiringEpisodeTime ?: 0L,
                            onClick = { onNavigateToMedia(media.id) },
                        )
                    }
                }
            }
        }

        // ── Continue Watching (merged: local resume + AniList CURRENT) ──
        run {
            // Local entries with resume position take priority
            val localIds = state.continueWatchingLocal.map { it.mediaId }.toSet()
            // AniList entries that aren't already in local history
            val anilistOnly = state.continueWatching.filter { it.id !in localIds }

            if (state.continueWatchingLocal.isNotEmpty() || anilistOnly.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Continue Watching",
                        actionText = "SEE ALL",
                        onAction = onNavigateToAnimeList,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Local entries first (have resume position bar)
                        items(
                            items = state.continueWatchingLocal,
                            key = { "watch_${it.mediaId}" },
                        ) { entry ->
                            WatchHistoryCard(
                                entry = entry,
                                showProgress = true,
                                onClick = { onNavigateToPlayer(entry.mediaId, entry.episodeNumber) },
                            )
                        }
                        // AniList entries that aren't in local history
                        items(
                            items = anilistOnly,
                            key = { "anilist_${it.id}" },
                        ) { media ->
                            MediaPosterCard(
                                title = media.displayTitle,
                                coverUrl = media.cover,
                                subtitle = media.episodeProgress,
                                onClick = { onNavigateToMedia(media.id) },
                            )
                        }
                    }
                }
            }
        }

        // ── Continue Reading (merged: local resume + AniList CURRENT) ──
        run {
            val localMangaIds = state.readingHistory.map { it.mangaId }.toSet()
            val anilistOnlyManga = state.continueReading.filter { it.id !in localMangaIds }

            if (state.readingHistory.isNotEmpty() || anilistOnlyManga.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Continue Reading",
                        actionText = "SEE ALL",
                        onAction = onNavigateToMangaList,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Local entries first (have page progress)
                        items(
                            items = state.readingHistory,
                            key = { "history_${it.mangaId}" },
                        ) { entry ->
                            MediaPosterCard(
                                title = entry.mangaTitle,
                                coverUrl = entry.coverUrl,
                                subtitle = "Ch. ${entry.chapterNumber} · p.${entry.lastPage + 1}/${entry.totalPages}",
                                onClick = { onNavigateToReader(entry.mangaId, entry.chapterNumber) },
                            )
                        }
                        // AniList entries that aren't in local history
                        items(
                            items = anilistOnlyManga,
                            key = { "anilist_manga_${it.id}" },
                        ) { media ->
                            MediaPosterCard(
                                title = media.displayTitle,
                                coverUrl = media.cover,
                                subtitle = media.episodeProgress,
                                onClick = { onNavigateToMedia(media.id) },
                            )
                        }
                    }
                }
            }
        }

        // ── Watch History (last 10) ──────────────────────────
        if (state.watchHistory.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = "History",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = state.watchHistory,
                        key = { "history_watch_${it.mediaId}_${it.episodeNumber}" },
                    ) { entry ->
                        WatchHistoryCard(
                            entry = entry,
                            showProgress = true,
                            onClick = { onNavigateToPlayer(entry.mediaId, entry.episodeNumber) },
                        )
                    }
                }
            }
        }

        // ── Recommended For You ──────────────────────────────
        if (state.recommendations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = "Recommended For You",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = state.recommendations,
                        key = { it.id },
                    ) { media ->
                        MediaPosterCard(
                            title = media.displayTitle,
                            coverUrl = media.cover,
                            onClick = { onNavigateToMedia(media.id) },
                        )
                    }
                }
            }
        }

        // Bottom spacing for nav bar clearance
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun WatchHistoryCard(
    entry: ani.saikou.data.local.db.WatchHistoryEntity,
    showProgress: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 170.dp)
                .clip(MaterialTheme.shapes.medium),
        ) {
            coil.compose.AsyncImage(
                model = entry.coverUrl,
                contentDescription = entry.mediaTitle,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (showProgress) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(entry.progressFraction)
                        .height(3.dp)
                        .background(ani.saikou.ui.theme.Primary),
                )
            }
        }
        Text(
            text = entry.mediaTitle,
            style = MaterialTheme.typography.titleSmall,
            color = OnSurface,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = "Ep ${entry.episodeNumber} • ${formatTime(entry.lastPositionMs)} / ${formatTime(entry.durationMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        contentPadding = 16.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AiringCard(
    title: String,
    coverUrl: String?,
    episodeNumber: Int,
    airingAtMs: Long,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 130.dp, height = 180.dp)
                .clip(MaterialTheme.shapes.medium),
        ) {
            coil.compose.AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Episode badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Primary, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "Ep $episodeNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.Black,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = OnSurface,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.width(130.dp),
        )
        Text(
            text = formatTimeUntil(airingAtMs),
            style = MaterialTheme.typography.bodySmall,
            color = Secondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun formatTimeUntil(airingAtMs: Long): String {
    val diff = airingAtMs - System.currentTimeMillis()
    if (diff <= 0) return "Airing now"

    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}
