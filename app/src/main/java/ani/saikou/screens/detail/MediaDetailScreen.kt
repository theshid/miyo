package ani.saikou.screens.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import ani.saikou.components.GlassCard
import ani.saikou.components.GenreChip
import ani.saikou.components.MediaPosterCard
import ani.saikou.components.PillButton
import ani.saikou.domain.model.Media
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.Favorite
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary
import ani.saikou.ui.theme.SurfaceContainer
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaDetailScreen(
    mediaId: Int,
    onBack: () -> Unit,
    onNavigateToCharacter: (Int) -> Unit,
    onNavigateToPlayer: (Int) -> Unit,
    onNavigateToReader: (Int) -> Unit,
    onNavigateToMedia: (Int) -> Unit,
    onNavigateToTorrent: ((String) -> Unit)? = null,
    viewModel: MediaDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
        }
        return
    }

    val media = state.media ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Collapsing Banner Header ─────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        ) {
            AsyncImage(
                model = media.banner ?: media.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Gradient fade to background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Background.copy(alpha = 0.6f),
                                Background,
                            ),
                            startY = 80f,
                        )
                    ),
            )
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnSurface,
                )
            }
        }

        // ── Poster + Title + Actions ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Poster
            AsyncImage(
                model = media.cover,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(100.dp)
                    .height(150.dp)
                    .clip(MaterialTheme.shapes.medium),
            )

            // Title + meta
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = media.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (media.nameRomaji != null && media.nameRomaji != media.displayTitle) {
                    Text(
                        text = media.nameRomaji,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    media.format?.let {
                        StatusBadge(text = it, color = Primary)
                    }
                    media.status?.let { status ->
                        val color = when (status) {
                            "RELEASING" -> Color(0xFF4CAF50)
                            "FINISHED" -> Secondary
                            else -> OnSurfaceVariant
                        }
                        StatusBadge(text = status, color = color)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Action Row ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                text = media.userStatus ?: "ADD TO LIST",
                onClick = { viewModel.updateStatus("CURRENT") },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.toggleFavorite() }) {
                Icon(
                    imageVector = if (media.isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (media.isFav) Favorite else OnSurfaceVariant,
                )
            }
            IconButton(onClick = { /* share */ }) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = OnSurfaceVariant,
                )
            }
            if (onNavigateToTorrent != null) {
                IconButton(onClick = { onNavigateToTorrent(media.displayTitle) }) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Torrent Search",
                        tint = OnSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Airing Countdown (if releasing) ──────────────────
        if (media.isOngoing && media.nextAiringEpisode != null) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "Ep ${media.nextAiringEpisode!! + 1} airing soon",
                    style = MaterialTheme.typography.titleSmall,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Tabbed Content ───────────────────────────────────
        val tabs = buildList {
            add("Info")
            if (media.type == "ANIME") add("Episodes") else add("Chapters")
            if (!media.characters.isNullOrEmpty()) add("Characters")
            if (!media.relations.isNullOrEmpty() || !media.recommendations.isNullOrEmpty()) add("Related")
        }

        var selectedTab by remember { mutableIntStateOf(0) }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = OnSurface,
            edgePadding = 16.dp,
            indicator = {},
            divider = {},
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedTab == index) Primary else OnSurfaceVariant,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Tab Content ──────────────────────────────────────
        when (tabs.getOrNull(selectedTab)) {
            "Info" -> InfoTab(media)
            "Episodes" -> EpisodesTab(
                totalEpisodes = media.totalEpisodes,
                userProgress = media.userProgress,
                onEpisodeClick = onNavigateToPlayer,
            )
            "Chapters" -> ChaptersTab(
                totalChapters = media.totalChapters,
                userProgress = media.userProgress,
                onChapterClick = onNavigateToReader,
            )
            "Characters" -> CharactersTab(
                characters = media.characters.orEmpty(),
                onCharacterClick = onNavigateToCharacter,
            )
            "Related" -> RelatedTab(
                relations = media.relations.orEmpty(),
                recommendations = media.recommendations.orEmpty(),
                onMediaClick = onNavigateToMedia,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoTab(media: Media) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Description
        if (!media.description.isNullOrBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Text(
                text = media.description.replace("<br>", "\n").replace(Regex("<[^>]*>"), ""),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .animateContentSize()
                    .clickable { expanded = !expanded },
            )
        }

        // Metadata grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            media.format?.let { MetadataRow("Format", it) }
            media.status?.let { MetadataRow("Status", it) }
            media.season?.let { s ->
                MetadataRow("Season", "$s ${media.seasonYear ?: ""}")
            }
            media.totalEpisodes?.let { MetadataRow("Episodes", "$it") }
            media.totalChapters?.let { MetadataRow("Chapters", "$it") }
            media.episodeDuration?.let { MetadataRow("Duration", "${it} min") }
            media.mainStudio?.let { MetadataRow("Studio", it) }
            media.meanScore?.let { MetadataRow("Score", "★ ${it / 10.0}") }
        }

        // Genres
        if (!media.genres.isNullOrEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                media.genres.forEach { genre ->
                    GenreChip(text = genre)
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EpisodesTab(
    totalEpisodes: Int?,
    userProgress: Int?,
    onEpisodeClick: (Int) -> Unit,
) {
    val count = totalEpisodes ?: 0
    if (count == 0) {
        Text(
            "No episode data available",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (ep in 1..count) {
            val watched = userProgress != null && ep <= userProgress
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onEpisodeClick(ep) },
                contentPadding = 12.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (watched) Primary.copy(alpha = 0.2f) else SurfaceContainer,
                                    MaterialTheme.shapes.small,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$ep",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (watched) Primary else OnSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "Episode $ep",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface,
                        )
                    }
                    if (watched) {
                        Text(
                            text = "WATCHED",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChaptersTab(
    totalChapters: Int?,
    userProgress: Int?,
    onChapterClick: (Int) -> Unit,
) {
    val count = totalChapters ?: 0
    if (count == 0) {
        Text(
            "No chapter data available",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (ch in 1..minOf(count, 50)) { // Cap at 50 to avoid perf issues in scroll
            val read = userProgress != null && ch <= userProgress
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onChapterClick(ch) },
                contentPadding = 12.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (read) Secondary.copy(alpha = 0.2f) else SurfaceContainer,
                                MaterialTheme.shapes.small,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$ch",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (read) Secondary else OnSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "Chapter $ch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharactersTab(
    characters: List<ani.saikou.domain.model.Character>,
    onCharacterClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 3-column grid as composables inside scroll
        val rows = characters.chunked(3)
        rows.forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { character ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCharacterClick(character.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AsyncImage(
                            model = character.image,
                            contentDescription = character.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(100.dp, 140.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )
                        Text(
                            text = character.name ?: "Unknown",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        character.role?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant,
                            )
                        }
                    }
                }
                // Fill remaining space if row has fewer than 3 items
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RelatedTab(
    relations: List<Media>,
    recommendations: List<Media>,
    onMediaClick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (relations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Relations",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = relations, key = { it.id }) { media ->
                        MediaPosterCard(
                            title = media.displayTitle,
                            coverUrl = media.cover,
                            onClick = { onMediaClick(media.id) },
                        )
                    }
                }
            }
        }

        if (recommendations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = recommendations, key = { it.id }) { media ->
                        MediaPosterCard(
                            title = media.displayTitle,
                            coverUrl = media.cover,
                            onClick = { onMediaClick(media.id) },
                        )
                    }
                }
            }
        }
    }
}
