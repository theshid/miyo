package ani.saikou.screens.lists

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.components.GenreChip
import ani.saikou.components.GlassCard
import ani.saikou.components.PillButton
import ani.saikou.domain.model.Media
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary
import ani.saikou.ui.theme.SurfaceBright
import ani.saikou.ui.theme.SurfaceContainer
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListsScreen(
    type: String,
    onBack: () -> Unit,
    onNavigateToMedia: (Int) -> Unit,
    viewModel: UserListsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var editingMedia by remember { mutableStateOf<Media?>(null) }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // ── Top Bar ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
            }
            Column {
                Text(
                    text = "My ${state.type} List",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Tracking ${state.items.size} titles in your archive",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Tab Row ──────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(UserListsViewModel.tabs) { tab ->
                GenreChip(
                    text = tab,
                    selected = tab == state.selectedTab,
                    onClick = { viewModel.selectTab(tab) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── List Content ─────────────────────────────────────
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
            }
        } else if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing here yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = state.items,
                    key = { it.id },
                ) { media ->
                    ListMediaCard(
                        media = media,
                        type = state.type,
                        onClick = { onNavigateToMedia(media.id) },
                        onLongClick = { editingMedia = media },
                    )
                }
            }
        }
    }

    // ── Edit Bottom Sheet ────────────────────────────────────
    editingMedia?.let { media ->
        EditBottomSheet(
            media = media,
            type = state.type,
            onDismiss = { editingMedia = null },
            onSave = { progress, score, status ->
                viewModel.updateEntry(media.id, progress, score, status)
                editingMedia = null
            },
        )
    }
}

@Composable
private fun ListMediaCard(
    media: Media,
    type: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(SurfaceContainer)
            .clickable(onClick = onClick),
    ) {
        // Banner background
        AsyncImage(
            model = media.banner ?: media.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Dark overlay
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(SurfaceContainer.copy(alpha = 0.95f), SurfaceContainer.copy(alpha = 0.7f)),
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Poster
            AsyncImage(
                model = media.cover,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(64.dp)
                    .height(96.dp)
                    .clip(MaterialTheme.shapes.small),
            )

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = media.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (media.meanScore != null) {
                            Text("★ ${media.meanScore / 10.0}", style = MaterialTheme.typography.bodySmall, color = Primary)
                        }
                        media.format?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                }

                // Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = if (type == "ANIME") "EPISODE PROGRESS" else "CHAPTER PROGRESS"
                    Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)

                    val cachedCounts by ani.saikou.data.local.MangaChapterCountCache.counts.collectAsState()
                    val progress = media.userProgress ?: 0
                    // Prefer AniList's count; fall back to the source-derived
                    // count we cached after the user opened the detail screen
                    // (covers Vagabond and other AniList-null cases).
                    val total = media.totalEpisodes
                        ?: media.totalChapters
                        ?: cachedCounts[media.id]
                    Text(
                        text = if (total != null && total > 0) "$progress / $total" else "$progress",
                        style = MaterialTheme.typography.labelLarge,
                        color = Secondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Progress bar at bottom — same source-cache fallback as above. When
        // even that comes back empty we render 0 (vs the old fallback of 1f,
        // which made any progress > 0 look like the user finished the series).
        val cachedCountsForBar by ani.saikou.data.local.MangaChapterCountCache.counts.collectAsState()
        val progress = media.userProgress?.toFloat() ?: 0f
        val total = (media.totalEpisodes ?: media.totalChapters ?: cachedCountsForBar[media.id])?.toFloat()
        val fraction = if (total != null && total > 0) (progress / total).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(fraction)
                .height(3.dp)
                .background(Primary),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBottomSheet(
    media: Media,
    type: String,
    onDismiss: () -> Unit,
    onSave: (progress: Int?, score: Int?, status: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var progressValue by remember { mutableFloatStateOf((media.userProgress ?: 0).toFloat()) }
    var scoreValue by remember { mutableFloatStateOf((media.userScore).toFloat()) }
    var selectedStatus by remember { mutableStateOf(media.userStatus ?: "CURRENT") }

    val maxProgress = (media.totalEpisodes ?: media.totalChapters ?: 100).toFloat()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceBright,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = media.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Status selector — only real MediaListStatus values, not Favorites
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(UserListsViewModel.statusMap.keys.toList()) { tab ->
                        val status = UserListsViewModel.statusMap[tab] ?: "CURRENT"
                        GenreChip(
                            text = tab,
                            selected = status == selectedStatus,
                            onClick = { selectedStatus = status },
                        )
                    }
                }
            }

            // Progress slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (type == "ANIME") "Episode Progress" else "Chapter Progress",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant,
                    )
                    Text(
                        "${progressValue.toInt()} / ${maxProgress.toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                    )
                }
                Slider(
                    value = progressValue,
                    onValueChange = { progressValue = it },
                    valueRange = 0f..maxProgress,
                    steps = maxProgress.toInt().coerceAtMost(100),
                    colors = SliderDefaults.colors(
                        thumbColor = Primary,
                        activeTrackColor = Primary,
                        inactiveTrackColor = SurfaceContainer,
                    ),
                )
            }

            // Score slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Score", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                    Text(
                        "${scoreValue.toInt()} / 100",
                        style = MaterialTheme.typography.labelMedium,
                        color = Secondary,
                    )
                }
                Slider(
                    value = scoreValue,
                    onValueChange = { scoreValue = it },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Secondary,
                        activeTrackColor = Secondary,
                        inactiveTrackColor = SurfaceContainer,
                    ),
                )
            }

            // Save button
            PillButton(
                text = "Save Changes",
                onClick = {
                    onSave(progressValue.toInt(), scoreValue.toInt(), selectedStatus)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
