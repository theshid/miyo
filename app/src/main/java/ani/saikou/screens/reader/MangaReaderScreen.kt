package ani.saikou.screens.reader

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ani.saikou.components.GenreChip
import ani.saikou.components.PillButton
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.SurfaceBright
import ani.saikou.ui.theme.SurfaceContainer
import ani.saikou.ui.theme.SurfaceContainerHigh
import coil.compose.AsyncImage

enum class ReadingDirection { VERTICAL, LEFT_TO_RIGHT, RIGHT_TO_LEFT }
enum class CanvasTheme(val bg: Color) {
    DARK(Color.Black),
    WHITE(Color.White),
    SEPIA(Color(0xFFF5E6C8)),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    mediaId: Int,
    chapterNum: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // Immersive mode
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    var showOverlay by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var direction by remember { mutableStateOf(ReadingDirection.VERTICAL) }
    var canvasTheme by remember { mutableStateOf(CanvasTheme.DARK) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }

    // TODO: Replace with actual chapter pages from manga source parser
    val totalPages = 24
    val pagerState = rememberPagerState(pageCount = { totalPages })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasTheme.bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { showOverlay = !showOverlay },
    ) {
        // ── Page content ─────────────────────────────────────
        when (direction) {
            ReadingDirection.VERTICAL -> {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    PageContent(
                        pageNum = page + 1,
                        totalPages = totalPages,
                        zoomLevel = zoomLevel,
                        canvasTheme = canvasTheme,
                    )
                }
            }
            ReadingDirection.LEFT_TO_RIGHT -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    PageContent(
                        pageNum = page + 1,
                        totalPages = totalPages,
                        zoomLevel = zoomLevel,
                        canvasTheme = canvasTheme,
                    )
                }
            }
            ReadingDirection.RIGHT_TO_LEFT -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                ) { page ->
                    PageContent(
                        pageNum = page + 1,
                        totalPages = totalPages,
                        zoomLevel = zoomLevel,
                        canvasTheme = canvasTheme,
                    )
                }
            }
        }

        // ── Minimal overlay ──────────────────────────────────
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
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
                                text = "Chapter $chapterNum",
                                style = MaterialTheme.typography.titleSmall,
                                color = OnSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "CHAPTER $chapterNum",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint = OnSurface)
                    }
                }

                // Page counter at bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(SurfaceContainer.copy(alpha = 0.8f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / $totalPages",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurface,
                    )
                }
            }
        }
    }

    // ── Settings Bottom Sheet ────────────────────────────────
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = SurfaceBright,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Reading Direction
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "READING DIRECTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GenreChip(
                            text = "VERTICAL",
                            selected = direction == ReadingDirection.VERTICAL,
                            onClick = { direction = ReadingDirection.VERTICAL },
                        )
                        GenreChip(
                            text = "L TO R",
                            selected = direction == ReadingDirection.LEFT_TO_RIGHT,
                            onClick = { direction = ReadingDirection.LEFT_TO_RIGHT },
                        )
                        GenreChip(
                            text = "R TO L",
                            selected = direction == ReadingDirection.RIGHT_TO_LEFT,
                            onClick = { direction = ReadingDirection.RIGHT_TO_LEFT },
                        )
                    }
                }

                // Canvas Theme
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "CANVAS THEME",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant,
                    )
                    Text(
                        text = "Visual comfort settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CanvasTheme.entries.forEach { theme ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(theme.bg)
                                    .then(
                                        if (canvasTheme == theme) Modifier
                                            .clip(CircleShape)
                                            .background(theme.bg)
                                        else Modifier
                                    )
                                    .clickable { canvasTheme = theme },
                            ) {
                                if (canvasTheme == theme) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .background(theme.bg)
                                    )
                                }
                            }
                        }
                    }
                }

                // Zoom
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { zoomLevel = (zoomLevel - 0.25f).coerceAtLeast(0.5f) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(SurfaceContainerHigh, CircleShape),
                        ) {
                            Icon(Icons.Default.Remove, "Zoom out", tint = OnSurface, modifier = Modifier.size(18.dp))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(zoomLevel * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "STANDARD",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                            )
                        }

                        IconButton(
                            onClick = { zoomLevel = (zoomLevel + 0.25f).coerceAtMost(3f) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(SurfaceContainerHigh, CircleShape),
                        ) {
                            Icon(Icons.Default.Add, "Zoom in", tint = OnSurface, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Apply button
                PillButton(
                    text = "Apply Changes",
                    onClick = { showSettings = false },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PageContent(
    pageNum: Int,
    totalPages: Int,
    zoomLevel: Float,
    canvasTheme: CanvasTheme,
) {
    // TODO: Replace with actual manga page image from source parser
    // For now, show a placeholder
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = zoomLevel,
                scaleY = zoomLevel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Placeholder — will be replaced with AsyncImage loading actual page URLs
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$pageNum",
                style = MaterialTheme.typography.displayLarge,
                color = if (canvasTheme == CanvasTheme.DARK) OnSurfaceVariant.copy(alpha = 0.2f)
                else Color.Gray.copy(alpha = 0.3f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Page $pageNum of $totalPages",
                style = MaterialTheme.typography.bodySmall,
                color = if (canvasTheme == CanvasTheme.DARK) OnSurfaceVariant.copy(alpha = 0.4f)
                else Color.Gray.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
