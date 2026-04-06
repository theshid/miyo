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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
            IconButton(onClick = { /* notifications */ }) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = OnSurfaceVariant,
                )
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
                value = "${state.user?.episodesWatched ?: 0}",
                label = "Episodes Watched",
                accentColor = Secondary,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = "${state.user?.chaptersRead ?: 0}",
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

        // ── Continue Watching ────────────────────────────────
        if (state.continueWatching.isNotEmpty()) {
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
                    items(
                        items = state.continueWatching,
                        key = { it.id },
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

        // ── Continue Reading ─────────────────────────────────
        if (state.continueReading.isNotEmpty()) {
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
                    items(
                        items = state.continueReading,
                        key = { it.id },
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
            )
        }
    }
}
