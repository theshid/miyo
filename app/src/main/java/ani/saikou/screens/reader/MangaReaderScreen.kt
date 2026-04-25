package ani.saikou.screens.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import ani.saikou.components.GenreChip
import ani.saikou.components.PillButton
import ani.saikou.components.TourOverlay
import ani.saikou.components.TourTarget
import ani.saikou.components.rememberTourState
import ani.saikou.components.tourTarget
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.SurfaceBright
import ani.saikou.ui.theme.SurfaceContainer
import ani.saikou.ui.theme.SurfaceContainerHigh
import coil.compose.AsyncImage

enum class ReadingMode { WEBTOON, PAGER_LTR, PAGER_RTL }

data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.WEBTOON,
    val background: Color = Color.White,
    val keepScreenOn: Boolean = true,
    val showPageNumber: Boolean = true,
    val doublePage: Boolean = false,
    val cropBorders: Boolean = false,
    val suggestDownloads: Boolean = true,
)

private val backgroundOptions = listOf(
    Color.Black,
    Color(0xFF2A2A2A),
    Color.White,
    Color(0xFFF5E6C8),
    Color(0xFFFFF8F0),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    mediaId: Int,
    chapterNum: Int,
    onBack: () -> Unit,
    onNextChapter: ((chapter: Int, sourceId: String?) -> Unit)? = null,
    viewModel: MangaReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val context = LocalContext.current
    val readerState by viewModel.uiState.collectAsState()

    val settingsStorage = remember { ReaderSettingsStorage(context) }
    val onboardingPrefs = remember { ani.saikou.di.AppModule.onboardingPrefs() }
    val tourState = rememberTourState()
    var showReaderTour by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(settingsStorage.load()) }

    // First-reader tour: once pages are loaded, force the controls overlay
    // visible so the chapter-list icon has a measured position, then show the
    // tour pointing at it.
    LaunchedEffect(readerState.isLoading, readerState.error) {
        if (readerState.isLoading || readerState.error != null) return@LaunchedEffect
        if (onboardingPrefs.hasSeenReaderTour()) return@LaunchedEffect
        showOverlay = true
        kotlinx.coroutines.delay(400) // let the overlay animate in + bounds settle
        showReaderTour = true
    }

    LaunchedEffect(settings) {
        settingsStorage.save(settings)
    }

    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Immersive mode
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Keep screen on
    DisposableEffect(settings.keepScreenOn) {
        val window = (context as Activity).window
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val totalPages = readerState.totalPages.coerceAtLeast(1)
    var currentPage by remember { mutableStateOf(1) }

    // Report page changes to ViewModel for history persistence
    LaunchedEffect(currentPage) {
        viewModel.onPageChanged(currentPage - 1) // 0-indexed for storage
    }

    // End-of-chapter download suggestion state
    val batchSize = 5
    var nextChapterMissing by remember(viewModel.mediaId, viewModel.chapterNum) { mutableStateOf<Boolean?>(null) }
    var suggestionDismissed by remember(viewModel.mediaId, viewModel.chapterNum) { mutableStateOf(false) }
    var showCellularConfirm by remember { mutableStateOf(false) }
    var estimatedBytes by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var downloadQueued by remember(viewModel.mediaId, viewModel.chapterNum) { mutableStateOf(false) }

    val showSuggestionBanner = settings.suggestDownloads &&
        !readerState.isLoading &&
        readerState.error == null &&
        totalPages > 1 &&
        currentPage == totalPages &&
        nextChapterMissing == true &&
        !suggestionDismissed &&
        !downloadQueued

    // Kick off the cache check once the user hits the last page.
    LaunchedEffect(currentPage, totalPages, settings.suggestDownloads) {
        if (!settings.suggestDownloads) return@LaunchedEffect
        if (nextChapterMissing != null) return@LaunchedEffect
        if (totalPages <= 1 || currentPage != totalPages) return@LaunchedEffect
        val missing = viewModel.isNextChapterMissing()
        nextChapterMissing = missing
        if (missing) {
            estimatedBytes = viewModel.estimateBytesForNext(batchSize)
        }
    }

    fun startBatchDownload() {
        viewModel.queueNextChapters(batchSize) {
            ani.saikou.data.local.downloads.DownloadService.start(context)
        }
        downloadQueued = true
    }

    // Chapter list sheet
    var showChapterList by remember { mutableStateOf(false) }
    val allChapters by viewModel.allChapters.collectAsState()
    val chapterListLoading by viewModel.chapterListLoading.collectAsState()
    val chapterDownloadStates by viewModel.chapterDownloads.collectAsState()
    LaunchedEffect(showChapterList) {
        if (showChapterList) viewModel.ensureChapterListLoaded()
    }

    // Loading
    if (readerState.isLoading && readerState.error == null) {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            ani.saikou.components.CatLoader(message = "Loading chapter...")
        }
        return
    }

    // Error screen
    if (readerState.error != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                coil.compose.AsyncImage(
                    model = ani.saikou.R.drawable.error_samurai,
                    contentDescription = "Error",
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .heightIn(max = 320.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Text(
                    text = readerState.error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(SurfaceContainer)
                            .clickable(onClick = onBack)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text("Go Back", color = OnSurface, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(Primary)
                            .clickable { viewModel.retry() }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(settings.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { showOverlay = !showOverlay },
    ) {
        // ── Page Content with Pinch-to-Zoom ──────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        ) {
            // Wrap the parent callback so inner readers keep their simple `(Int) -> Unit`
            // signature but the resolved source id is forwarded.
            val onNextChapterWithSource: ((Int) -> Unit)? = onNextChapter?.let { cb ->
                { next -> cb(next, readerState.resolvedSourceId) }
            }
            when (settings.mode) {
                ReadingMode.WEBTOON -> {
                    WebtoonReader(
                        pages = readerState.pages,
                        totalPages = totalPages,
                        background = settings.background,
                        startPage = readerState.startPage,
                        onPageChanged = { currentPage = it },
                        onNextChapter = onNextChapterWithSource,
                        chapterNum = chapterNum,
                    )
                }
                ReadingMode.PAGER_LTR -> {
                    PagerReader(
                        pages = readerState.pages,
                        totalPages = totalPages,
                        reverseLayout = false,
                        startPage = readerState.startPage,
                        onPageChanged = { currentPage = it },
                        onNextChapter = onNextChapterWithSource,
                        chapterNum = chapterNum,
                    )
                }
                ReadingMode.PAGER_RTL -> {
                    PagerReader(
                        pages = readerState.pages,
                        totalPages = totalPages,
                        reverseLayout = true,
                        startPage = readerState.startPage,
                        onPageChanged = { currentPage = it },
                        onNextChapter = onNextChapterWithSource,
                        chapterNum = chapterNum,
                    )
                }
            }
        }

        // ── Double-tap to reset zoom ─────────────────────────
        // (Single tap toggles overlay, handled above)

        // ── Overlay ──────────────────────────────────────────
        AnimatedVisibility(visible = showOverlay, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                        }
                        Column {
                            Text(
                                text = readerState.title.ifEmpty { "Chapter $chapterNum" },
                                style = MaterialTheme.typography.titleSmall,
                                color = OnSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(readerState.chapterTitle, style = MaterialTheme.typography.labelSmall, color = Primary)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showChapterList = true },
                            modifier = Modifier.tourTarget(tourState, TourTarget.READER_CHAPTERS),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                "Chapters",
                                tint = OnSurface,
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "Settings", tint = OnSurface)
                        }
                    }
                }

                // Bottom bar: page counter + zoom reset
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (settings.showPageNumber) {
                        Text(
                            "$currentPage / $totalPages",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurface,
                        )
                    }
                    if (scale != 1f) {
                        GenreChip(text = "Reset Zoom", selected = true, onClick = {
                            scale = 1f; offsetX = 0f; offsetY = 0f
                        })
                    }
                    Text(
                        "${(scale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                    )
                }
            }
        }

        // ── End-of-chapter download suggestion ───────────────
        AnimatedVisibility(
            visible = showSuggestionBanner,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            DownloadNextChaptersBanner(
                count = batchSize,
                estimateLabel = if (estimatedBytes > 0) viewModel.formatBytes(estimatedBytes) else null,
                onDownload = {
                    val unmetered = ani.saikou.di.AppModule.connectivity().isUnmetered()
                    if (unmetered) {
                        startBatchDownload()
                    } else {
                        showCellularConfirm = true
                    }
                },
                onDismiss = { suggestionDismissed = true },
            )
        }
    }

    // ── Cellular download confirmation ────────────────────────
    if (showCellularConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCellularConfirm = false },
            title = { Text("Download on cellular?", color = OnSurface) },
            text = {
                Text(
                    "You're not on Wi-Fi. Downloading $batchSize chapters will use about " +
                        "${viewModel.formatBytes(estimatedBytes)} of data.",
                    color = OnSurfaceVariant,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showCellularConfirm = false
                    startBatchDownload()
                }) {
                    Text("Download", color = Primary)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCellularConfirm = false }) {
                    Text("Cancel", color = OnSurfaceVariant)
                }
            },
            containerColor = SurfaceContainerHigh,
        )
    }

    // ── Source selector ───────────────────────────────────────
    if (readerState.showSourceSelector) {
        ani.saikou.components.SourceSelectorSheet(
            title = "Select Manga Source",
            sources = readerState.availableSources,
            onSelect = { source -> viewModel.selectSourceById(source.id) },
            onDismiss = { viewModel.dismissSourceSelector() },
        )
    }

    // ── Chapter list sheet ───────────────────────────────────
    if (showChapterList) {
        val localOnlyNumbers = chapterDownloadStates.keys
        val parserNumbers = allChapters.map { it.number.toInt() }
        val mergedNumbers = (parserNumbers + localOnlyNumbers)
            .toSortedSet()
            .toList()
        ReaderChapterListSheet(
            currentChapterNumber = chapterNum,
            userProgress = null, // the reader doesn't track AniList progress directly here
            chapterNumbers = mergedNumbers,
            downloadStates = chapterDownloadStates,
            isLoading = chapterListLoading,
            onDismiss = { showChapterList = false },
            onJumpToChapter = { target ->
                showChapterList = false
                onNextChapter?.invoke(target, readerState.resolvedSourceId)
            },
            onDownloadClick = { ch ->
                viewModel.queueSingleChapterDownload(ch) {
                    ani.saikou.data.local.downloads.DownloadService.start(context)
                }
            },
            onCancelDownloadClick = { ch ->
                viewModel.cancelDownload(ch)
            },
        )
    }

    // ── Settings Sheet ───────────────────────────────────────
    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            onSettingsChange = { settings = it },
            onDismiss = { showSettings = false },
        )
    }

    // ── First-reader tour ────────────────────────────────────
    if (showReaderTour) {
        var tourStep by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        TourOverlay(
            steps = ani.saikou.components.DefaultReaderTourSteps,
            state = tourState,
            currentStep = tourStep,
            onStepChanged = { tourStep = it },
            onComplete = {
                showReaderTour = false
                onboardingPrefs.markReaderTourSeen()
            },
        )
    }
}

// ── Webtoon (vertical scroll) reader ─────────────────────────
@Composable
private fun WebtoonReader(
    pages: List<ani.saikou.domain.model.MangaPage>,
    totalPages: Int,
    background: Color,
    startPage: Int = 0,
    onPageChanged: (Int) -> Unit,
    onNextChapter: ((Int) -> Unit)? = null,
    chapterNum: Int = 0,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)

    // Track the furthest-visible page so the reader can detect "end of chapter"
    // reliably even when several short webtoon panels share the viewport.
    val currentPageIndex by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: listState.firstVisibleItemIndex
        }
    }
    LaunchedEffect(currentPageIndex) {
        onPageChanged(currentPageIndex + 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(pages) { index, page ->
            val model = if (page.headers.isNotEmpty()) {
                coil.request.ImageRequest.Builder(context)
                    .data(page.imageUrl)
                    .apply { page.headers.forEach { (k, v) -> addHeader(k, v) } }
                    .crossfade(true)
                    .build()
            } else {
                page.imageUrl
            }
            coil.compose.SubcomposeAsyncImage(
                model = model,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(500.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ani.saikou.components.CatLoader(
                            message = "Page ${index + 1}",
                            size = 80.dp,
                        )
                    }
                },
            )
        }
        if (pages.isEmpty()) {
            items(totalPages) { index ->
                Box(
                    Modifier.fillMaxWidth().height(500.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Page ${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
            }
        }
        // Next Chapter card at the end
        if (pages.isNotEmpty() && onNextChapter != null) {
            item(key = "next_chapter") {
                NextChapterCard(
                    nextChapterNum = chapterNum + 1,
                    onClick = { onNextChapter(chapterNum + 1) },
                )
            }
        }
    }
}

// ── Pager (horizontal) reader ────────────────────────────────
@Composable
private fun PagerReader(
    pages: List<ani.saikou.domain.model.MangaPage>,
    totalPages: Int,
    reverseLayout: Boolean,
    startPage: Int = 0,
    onPageChanged: (Int) -> Unit,
    onNextChapter: ((Int) -> Unit)? = null,
    chapterNum: Int = 0,
) {
    val context = LocalContext.current
    val hasNextPage = pages.isNotEmpty() && onNextChapter != null
    val pageCount = (if (pages.isNotEmpty()) pages.size else totalPages) + if (hasNextPage) 1 else 0

    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { pageCount },
    )

    // Track current page (don't count the bonus "next chapter" page)
    LaunchedEffect(pagerState.currentPage) {
        val displayPage = (pagerState.currentPage + 1).coerceAtMost(if (pages.isNotEmpty()) pages.size else totalPages)
        onPageChanged(displayPage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = reverseLayout,
    ) { page ->
        // Last page is the "Next Chapter" card
        if (hasNextPage && page == pageCount - 1) {
            NextChapterCard(
                nextChapterNum = chapterNum + 1,
                onClick = { onNextChapter!!(chapterNum + 1) },
            )
        } else {
            val mangaPage = pages.getOrNull(page)
            if (mangaPage != null) {
                val model = if (mangaPage.headers.isNotEmpty()) {
                    coil.request.ImageRequest.Builder(context)
                        .data(mangaPage.imageUrl)
                        .apply { mangaPage.headers.forEach { (k, v) -> addHeader(k, v) } }
                        .crossfade(true)
                        .build()
                } else {
                    mangaPage.imageUrl
                }
                coil.compose.SubcomposeAsyncImage(
                    model = model,
                    contentDescription = "Page ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            ani.saikou.components.CatLoader(
                                message = "Page ${page + 1}",
                                size = 80.dp,
                            )
                        }
                    },
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Page ${page + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

// ── Download Next N Banner ──────────────────────────────────
@Composable
private fun DownloadNextChaptersBanner(
    count: Int,
    estimateLabel: String?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(SurfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Save the next $count chapters?",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (estimateLabel != null) {
                Text(
                    text = "About $estimateLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(Primary)
                .clickable(onClick = onDownload)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Download",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Next Chapter Card ────────────────────────────────────────
@Composable
private fun NextChapterCard(
    nextChapterNum: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "End of chapter",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Continue to the next chapter?",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            PillButton(
                text = "CHAPTER $nextChapterNum",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
        }
    }
}

// ── Settings Bottom Sheet ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceBright,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Primary)
                }
                Text(
                    "READER SETTINGS",
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                )
                // Spacer for alignment
                Spacer(Modifier.size(48.dp))
            }

            // ── Reading Mode ─────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("READING MODE", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GenreChip(
                        text = "WEBTOON ↕",
                        selected = settings.mode == ReadingMode.WEBTOON,
                        onClick = { onSettingsChange(settings.copy(mode = ReadingMode.WEBTOON)) },
                    )
                    GenreChip(
                        text = "PAGER →",
                        selected = settings.mode == ReadingMode.PAGER_LTR,
                        onClick = { onSettingsChange(settings.copy(mode = ReadingMode.PAGER_LTR)) },
                    )
                    GenreChip(
                        text = "PAGER ←",
                        selected = settings.mode == ReadingMode.PAGER_RTL,
                        onClick = { onSettingsChange(settings.copy(mode = ReadingMode.PAGER_RTL)) },
                    )
                }
            }

            // ── Background ───────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BACKGROUND", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    backgroundOptions.forEach { color ->
                        val isSelected = settings.background == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                    else Modifier
                                )
                                .clickable { onSettingsChange(settings.copy(background = color)) },
                        ) {
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Primary),
                                )
                            }
                        }
                    }
                }
            }

            // ── Brightness ───────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BRIGHTNESS", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Brightness6, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp))
                    Slider(
                        value = 0.8f, // Placeholder — would need WindowManager.LayoutParams.screenBrightness
                        onValueChange = { /* set brightness */ },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Primary,
                            inactiveTrackColor = SurfaceContainerHigh,
                        ),
                    )
                    Icon(Icons.Default.Brightness6, null, tint = OnSurface, modifier = Modifier.size(22.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = true,
                        onCheckedChange = {},
                        colors = CheckboxDefaults.colors(checkedColor = Primary),
                    )
                    Text("Use system brightness", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }

            // ── Display Toggles ──────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DISPLAY", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)

                SettingsToggle(
                    label = "Keep screen on",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { onSettingsChange(settings.copy(keepScreenOn = it)) },
                )
                SettingsToggle(
                    label = "Show page number",
                    checked = settings.showPageNumber,
                    onCheckedChange = { onSettingsChange(settings.copy(showPageNumber = it)) },
                )
                SettingsToggle(
                    label = "Double-page landscape",
                    checked = settings.doublePage,
                    onCheckedChange = { onSettingsChange(settings.copy(doublePage = it)) },
                )
                SettingsToggle(
                    label = "Crop borders",
                    checked = settings.cropBorders,
                    onCheckedChange = { onSettingsChange(settings.copy(cropBorders = it)) },
                )
                SettingsToggle(
                    label = "Suggest downloads at end of chapter",
                    checked = settings.suggestDownloads,
                    onCheckedChange = { onSettingsChange(settings.copy(suggestDownloads = it)) },
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnSurface,
                checkedTrackColor = Primary,
                uncheckedThumbColor = OnSurfaceVariant,
                uncheckedTrackColor = SurfaceContainer,
            ),
        )
    }
}
