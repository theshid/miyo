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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.components.ChapterDownloadState
import ani.saikou.components.ChapterRow
import ani.saikou.components.GlassCard
import ani.saikou.components.GenreChip
import ani.saikou.components.MediaPosterCard
import ani.saikou.components.PillButton
import ani.saikou.components.SourceItem
import ani.saikou.components.SourceSelectorSheet
import ani.saikou.data.remote.parsers.GogoParser
import ani.saikou.domain.model.AnimeSource
import ani.saikou.domain.model.MangaSearchResult
import ani.saikou.domain.model.Media
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.Favorite
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary
import ani.saikou.ui.theme.SurfaceContainer
import ani.saikou.di.AppModule
import ani.saikou.util.ShareCardGenerator
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaDetailScreen(
    mediaId: Int,
    onBack: () -> Unit,
    onNavigateToCharacter: (Int) -> Unit,
    onNavigateToPlayer: (Int, String?) -> Unit,
    onNavigateToReader: (Int, String?) -> Unit,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Catch Me Up state ──
    var showCatchMeUp by remember { mutableStateOf(false) }

    // ── Source picker state (shown before navigating to player) ──
    var pendingEpisode by remember { mutableStateOf<Int?>(null) }
    var sourceSearching by remember { mutableStateOf(false) }
    var foundSources by remember { mutableStateOf<List<AnimeSource>>(emptyList()) }
    var showSourcePicker by remember { mutableStateOf(false) }

    fun onEpisodeSelected(episodeNum: Int) {
        // Check watch history first — if source is saved, go directly
        scope.launch {
            val historyDao = AppModule.watchHistoryDao()
            val history = historyDao.getForMedia(mediaId)
            if (history != null && history.sourceSlug.isNotEmpty()) {
                onNavigateToPlayer(episodeNum, history.sourceSlug)
                return@launch
            }

            // Search for sources
            sourceSearching = true
            pendingEpisode = episodeNum
            val parser = GogoParser()
            val title = media.nameRomaji ?: media.name ?: media.displayTitle
            val sources = parser.search(title)
            sourceSearching = false

            when {
                sources.isEmpty() -> {
                    // No sources — navigate anyway, let the player show the error
                    onNavigateToPlayer(episodeNum, null)
                }
                sources.size == 1 -> {
                    onNavigateToPlayer(episodeNum, sources.first().slug)
                }
                else -> {
                    foundSources = sources
                    showSourcePicker = true
                }
            }
        }
    }

    // Source picker bottom sheet
    if (showSourcePicker) {
        SourceSelectorSheet(
            title = "Select Source",
            sources = foundSources.map { SourceItem(id = it.slug, title = it.name, coverUrl = it.cover) },
            onSelect = { source ->
                showSourcePicker = false
                val ep = pendingEpisode ?: return@SourceSelectorSheet
                onNavigateToPlayer(ep, source.id)
            },
            onDismiss = {
                showSourcePicker = false
                pendingEpisode = null
            },
        )
    }

    // ── Manga source picker state ──
    var pendingChapter by remember { mutableStateOf<Int?>(null) }
    var mangaSourceSearching by remember { mutableStateOf(false) }
    var foundMangaSearchResults by remember { mutableStateOf<List<MangaSearchResult>>(emptyList()) }
    var showMangaSearchResultPicker by remember { mutableStateOf(false) }

    fun onChapterSelected(chapterNum: Int) {
        scope.launch {
            val historyDao = AppModule.readingHistoryDao()
            val history = historyDao.getForManga(mediaId)
            if (history != null && history.sourceId.isNotEmpty()) {
                onNavigateToReader(chapterNum, history.sourceId)
                return@launch
            }

            mangaSourceSearching = true
            pendingChapter = chapterNum
            val title = media.nameRomaji ?: media.name ?: media.displayTitle

            // Try MangaDex first, then MangaPill as fallback
            var sources = AppModule.mangaDexParser().search(title)
            if (sources.isEmpty()) {
                sources = AppModule.mangaPillParser().search(title)
            }
            mangaSourceSearching = false

            when {
                sources.isEmpty() -> onNavigateToReader(chapterNum, null)
                sources.size == 1 -> onNavigateToReader(chapterNum, sources.first().id)
                else -> {
                    foundMangaSearchResults = sources
                    showMangaSearchResultPicker = true
                }
            }
        }
    }

    if (showMangaSearchResultPicker) {
        SourceSelectorSheet(
            title = "Select Manga Source",
            sources = foundMangaSearchResults.map { SourceItem(id = it.id, title = it.title, coverUrl = it.coverUrl) },
            onSelect = { source ->
                showMangaSearchResultPicker = false
                val ch = pendingChapter ?: return@SourceSelectorSheet
                onNavigateToReader(ch, source.id)
            },
            onDismiss = {
                showMangaSearchResultPicker = false
                pendingChapter = null
            },
        )
    }

    // Catch Me Up bottom sheet
    if (showCatchMeUp) {
        ModalBottomSheet(
            onDismissRequest = { showCatchMeUp = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Background,
        ) {
            CatchMeUpSheet(
                media = media,
                onDismiss = { showCatchMeUp = false },
            )
        }
    }

    // Loading overlay while searching for sources
    if (sourceSearching || mangaSourceSearching) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(SurfaceContainer, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                    Text("Finding sources...", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }
    }

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
                val romaji = media.nameRomaji
                if (romaji != null && romaji != media.displayTitle) {
                    Text(
                        text = romaji,
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
            // Status picker with dropdown (shows current status or "ADD TO LIST")
            Box(modifier = Modifier.weight(1f)) {
                var statusMenuOpen by remember { mutableStateOf(false) }
                PillButton(
                    text = displayStatusLabel(media.userStatus, media.type),
                    onClick = {
                        if (media.userStatus == null) {
                            viewModel.updateStatus("CURRENT")
                        } else {
                            statusMenuOpen = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = statusMenuOpen,
                    onDismissRequest = { statusMenuOpen = false },
                    modifier = Modifier.background(ani.saikou.ui.theme.SurfaceContainerHigh),
                ) {
                    listOf(
                        "CURRENT" to (if (media.type == "MANGA") "Reading" else "Watching"),
                        "PLANNING" to "Planning",
                        "COMPLETED" to "Completed",
                        "PAUSED" to "Paused",
                        "DROPPED" to "Dropped",
                        "REPEATING" to (if (media.type == "MANGA") "Rereading" else "Rewatching"),
                    ).forEach { (value, label) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (media.userStatus == value) Primary else OnSurface,
                                    fontWeight = if (media.userStatus == value) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                statusMenuOpen = false
                                viewModel.updateStatus(value)
                            },
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(color = ani.saikou.ui.theme.OutlineVariant)
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                text = "Remove from list",
                                color = ani.saikou.ui.theme.Error,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        onClick = {
                            statusMenuOpen = false
                            viewModel.removeFromList()
                        },
                    )
                }
            }
            IconButton(onClick = { viewModel.toggleFavorite() }) {
                Icon(
                    imageVector = if (media.isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (media.isFav) Favorite else OnSurfaceVariant,
                )
            }
            IconButton(onClick = {
                scope.launch {
                    val user = AppModule.repository().getUserData()
                    val bitmap = ShareCardGenerator.generateWatchingCard(
                        context = context,
                        title = media.displayTitle,
                        coverUrl = media.cover,
                        episodeProgress = media.userProgress,
                        totalEpisodes = media.totalEpisodes,
                        userScore = if (media.userScore > 0) media.userScore else null,
                        userName = user?.name,
                    )
                    val uri = ShareCardGenerator.saveToCacheAndGetUri(context, bitmap)
                    ShareCardGenerator.shareImage(
                        context = context,
                        uri = uri,
                        text = "${media.displayTitle} — tracked on Miyo",
                    )
                }
            }) {
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
            // Catch Me Up — only shown when user has progress on this series
            val progress = media.userProgress
            if (progress != null && progress > 0) {
                IconButton(onClick = { showCatchMeUp = true }) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Catch Me Up",
                        tint = Primary,
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
                onEpisodeClick = ::onEpisodeSelected,
            )
            "Chapters" -> ChaptersTab(
                mediaId = media.id,
                totalChapters = media.totalChapters,
                userProgress = media.userProgress,
                mediaTitle = media.nameRomaji ?: media.name ?: media.displayTitle,
                onChapterClick = ::onChapterSelected,
                downloadStates = viewModel.chapterDownloads.collectAsState().value,
                onDownloadClick = { chapterNum ->
                    viewModel.queueChapterDownload(chapterNum) {
                        ani.saikou.data.local.downloads.DownloadService.start(context)
                    }
                },
                onCancelDownloadClick = viewModel::cancelChapterDownload,
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
private fun displayStatusLabel(status: String?, type: String?): String {
    val isManga = type == "MANGA"
    return when (status) {
        null -> "ADD TO LIST"
        "CURRENT" -> if (isManga) "READING" else "WATCHING"
        "PLANNING" -> "PLANNING"
        "COMPLETED" -> "COMPLETED"
        "PAUSED" -> "PAUSED"
        "DROPPED" -> "DROPPED"
        "REPEATING" -> if (isManga) "REREADING" else "REWATCHING"
        else -> status
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
        val description = media.description
        if (!description.isNullOrBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Text(
                text = description.replace("<br>", "\n").replace(Regex("<[^>]*>"), ""),
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
        val genres = media.genres
        if (!genres.isNullOrEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { genre ->
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

    // Partition episodes into 100-range buckets — keeps long series (One Piece, Conan) performant
    // by rendering only ~100 GlassCards at a time instead of 1000+.
    val bucketSize = 100
    val buckets = remember(count) {
        (1..count step bucketSize).map { start ->
            start..minOf(start + bucketSize - 1, count)
        }
    }

    // Bucket containing the next episode to watch — used as the default selection
    // and as a secondary highlight so users can jump back to "where they left off".
    val nextEpisode = ((userProgress ?: 0) + 1).coerceIn(1, count)
    val progressBucketIndex = buckets.indexOfFirst { nextEpisode in it }.coerceAtLeast(0)

    var selectedBucketIndex by rememberSaveable(count) { mutableIntStateOf(progressBucketIndex) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Range chips — only when there's more than one bucket (skip for normal 12/24-ep shows).
        if (buckets.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(buckets.size) { index ->
                    val range = buckets[index]
                    val isSelected = index == selectedBucketIndex
                    val isCurrent = index == progressBucketIndex
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                when {
                                    isSelected -> Primary
                                    isCurrent -> Primary.copy(alpha = 0.15f)
                                    else -> SurfaceContainer
                                },
                            )
                            .clickable { selectedBucketIndex = index }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "${range.first}-${range.last}",
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                isSelected -> Color.Black
                                isCurrent -> Primary
                                else -> OnSurface
                            },
                            fontWeight = if (isSelected || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        val visibleRange = buckets.getOrNull(selectedBucketIndex) ?: (1..count)
        for (ep in visibleRange) {
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
    mediaId: Int,
    totalChapters: Int?,
    userProgress: Int?,
    mediaTitle: String?,
    onChapterClick: (Int) -> Unit,
    downloadStates: Map<Int, ChapterDownloadState> = emptyMap(),
    onDownloadClick: (Int) -> Unit = {},
    onCancelDownloadClick: (Int) -> Unit = {},
) {
    // For ongoing manga, AniList often returns null/0 for totalChapters.
    // Fetch actual chapter count from sources in the background.
    var sourceChapterCount by remember { mutableStateOf<Int?>(null) }
    var loadingCount by remember { mutableStateOf(false) }

    // Always probe the sources, even when AniList has a chapter count, since
    // AniList sometimes reports a low/stale number for licensed or on-hiatus
    // titles (e.g. Vagabond shows 5 here while MangaPill has 327).
    androidx.compose.runtime.LaunchedEffect(mediaTitle) {
        if (mediaTitle != null && !loadingCount) {
            loadingCount = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val resolved = AppModule.mangaSourceRepository().resolveChapterCount(
                    title = mediaTitle,
                    anilistTotal = totalChapters,
                )
                sourceChapterCount = resolved
                // Stash the resolved count so other screens (lists, continue
                // reading, etc.) can render the real number instead of "?"
                // when AniList comes back null for this title. The cache is
                // keyed by AniList mediaId — that mapping isn't visible to
                // the repo, so the put() stays here.
                if (resolved != null) {
                    ani.saikou.data.local.MangaChapterCountCache.put(mediaId, resolved)
                }
            }
            loadingCount = false
        }
    }

    // Prefer the source count when it's notably higher than what AniList
    // reported — covers the Vagabond case where AniList returns null but the
    // source actually has 327. The 1.5x guard avoids flipping on minor diffs.
    val count = when {
        sourceChapterCount != null && sourceChapterCount!! > (totalChapters ?: 0) * 1.5 -> sourceChapterCount!!
        totalChapters != null && totalChapters > 0 -> totalChapters
        sourceChapterCount != null && sourceChapterCount!! > 0 -> sourceChapterCount!!
        else -> 0
    }

    if (count == 0 && loadingCount) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                Text("Fetching chapters from source...", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
        }
        return
    }

    if (count == 0) {
        Text(
            "No chapters found",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    val bucketSize = 100
    val totalBuckets = (count + bucketSize - 1) / bucketSize
    var selectedBucket by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Bucket selector for large chapter counts
        if (totalBuckets > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(count = totalBuckets, key = { it }) { bucket ->
                    val start = bucket * bucketSize + 1
                    val end = minOf((bucket + 1) * bucketSize, count)
                    GenreChip(
                        text = "$start–$end",
                        selected = bucket == selectedBucket,
                        onClick = { selectedBucket = bucket },
                    )
                }
            }
        }

        val rangeStart = selectedBucket * bucketSize + 1
        val rangeEnd = minOf((selectedBucket + 1) * bucketSize, count)

        for (ch in rangeStart..rangeEnd) {
            ChapterRow(
                chapterNumber = ch,
                read = userProgress != null && ch <= userProgress,
                downloadState = downloadStates[ch],
                onClick = { onChapterClick(ch) },
                onDownloadClick = { onDownloadClick(ch) },
                onCancelDownloadClick = { onCancelDownloadClick(ch) },
            )
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
