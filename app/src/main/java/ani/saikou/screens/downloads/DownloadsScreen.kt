package ani.saikou.screens.downloads

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.domain.model.Download
import ani.saikou.domain.model.DownloadStatus
import ani.saikou.sharedui.components.GenreChip
import ani.saikou.sharedui.components.HalftoneButton
import ani.saikou.sharedui.components.HalftoneSize
import ani.saikou.sharedui.components.HalftoneVariant
import ani.saikou.sharedui.theme.OnSurface
import ani.saikou.sharedui.theme.OnSurfaceVariant
import ani.saikou.sharedui.theme.Primary
import ani.saikou.sharedui.theme.SurfaceContainer
import ani.saikou.sharedui.theme.SurfaceContainerHigh
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val selectedIds = remember { mutableStateListOf<String>() }
    var showCleanupDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (selectedIds.isNotEmpty()) 80.dp else 16.dp),
        ) {
            // ── Top Bar ──────────────────────────────────────
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                        }
                        Text(
                            text = "Downloads",
                            style = MaterialTheme.typography.titleLarge,
                            color = OnSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = formatSize(state.totalStorageUsed) + " USED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                    )
                }
            }

            // ── Storage Bar ──────────────────────────────────
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Device Storage", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        Text(
                            "${formatSize(state.freeSpace)} Free",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }
                    val usedFraction =
                        if (state.freeSpace + state.totalStorageUsed > 0) {
                            state.totalStorageUsed.toFloat() / (state.freeSpace + state.totalStorageUsed)
                        } else {
                            0f
                        }
                    LinearProgressIndicator(
                        progress = { usedFraction.coerceIn(0f, 1f) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(MaterialTheme.shapes.extraSmall),
                        color = Primary,
                        trackColor = SurfaceContainerHigh,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            // ── Cleanup banner: chapters you've already read ─
            if (state.readChapterCount > 0) {
                item {
                    Row(
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(SurfaceContainerHigh)
                                .clickable { showCleanupDialog = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${state.readChapterCount} chapters read",
                                style = MaterialTheme.typography.titleSmall,
                                color = OnSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Free up ${formatSize(state.readChapterBytes)} by clearing chapters you've finished.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                        }
                        Text(
                            "CLEAR",
                            style = MaterialTheme.typography.labelLarge,
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // ── Filter Tabs ──────────────────────────────────
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GenreChip(text = "MANGA", selected = true, onClick = {})
                    GenreChip(text = "APPS & OTHERS", onClick = {})
                }
            }

            // ── Active Downloads ─────────────────────────────
            val activeDownloads =
                state.mangaList
                    .flatMap { it.chapters }
                    .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }

            if (activeDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "⬇ DOWNLOADING",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                items(activeDownloads, key = { "active_${it.id}" }) { download ->
                    ActiveDownloadCard(
                        download = download,
                        coverUrl =
                            state.mangaList
                                .find { it.manga.mangaId == download.mangaId }
                                ?.manga
                                ?.coverUrl,
                        onPause = { viewModel.pauseDownload(download.id) },
                    )
                }
            }

            // ── Library Storage ──────────────────────────────
            val completedManga =
                state.mangaList.filter { group ->
                    group.chapters.any { it.status == DownloadStatus.COMPLETED }
                }

            if (completedManga.isNotEmpty()) {
                item {
                    Text(
                        text = "LIBRARY STORAGE",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                completedManga.forEach { mangaGroup ->
                    item(key = "manga_${mangaGroup.manga.mangaId}") {
                        MangaGroupCard(
                            mangaGroup = mangaGroup,
                            selectedIds = selectedIds,
                            onToggleSelect = { id ->
                                if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
                            },
                        )
                    }
                }
            }

            // ── Empty State ──────────────────────────────────
            if (state.mangaList.isEmpty() && !state.isLoading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No downloads yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Download chapters from the manga reader",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }

        // ── Delete Selected Bottom Bar ───────────────────────
        if (selectedIds.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(SurfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selectedIds.size} SELECTED",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                )
                HalftoneButton(
                    text = "DELETE SELECTED",
                    onClick = {
                        selectedIds.toList().forEach { viewModel.deleteChapter(it) }
                        selectedIds.clear()
                    },
                    size = HalftoneSize.LG,
                    variant = HalftoneVariant.PURPLE,
                    geistFamily = ani.saikou.sharedui.theme.Inter,
                )
            }
        }

        if (showCleanupDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCleanupDialog = false },
                title = { Text("Clear read chapters?", color = OnSurface) },
                text = {
                    Text(
                        "This will delete ${state.readChapterCount} chapters " +
                            "(${formatSize(state.readChapterBytes)}) you've already read. " +
                            "Unread and in-progress chapters stay put.",
                        color = OnSurfaceVariant,
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showCleanupDialog = false
                        viewModel.clearReadChapters()
                    }) {
                        Text("Clear", color = Primary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showCleanupDialog = false }) {
                        Text("Cancel", color = OnSurfaceVariant)
                    }
                },
                containerColor = SurfaceContainerHigh,
            )
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    download: Download,
    coverUrl: String?,
    onPause: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(SurfaceContainer)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover with circular progress ring
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.small),
            )
            val progress = if (download.totalPages > 0) download.downloadedPages.toFloat() / download.totalPages else 0f
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp),
                color = Primary,
                trackColor = Color.Black.copy(alpha = 0.5f),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = download.mangaTitle,
                style = MaterialTheme.typography.titleSmall,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Ch. ${download.chapterKey}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            val pct = if (download.totalPages > 0) (download.downloadedPages * 100 / download.totalPages) else 0
            Text(
                text = "$pct% COMPLETE    ${download.downloadedPages}/${download.totalPages} PAGES",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
            )
        }

        IconButton(
            onClick = onPause,
            modifier =
                Modifier
                    .size(36.dp)
                    .background(SurfaceContainerHigh, CircleShape),
        ) {
            Icon(Icons.Default.Pause, "Pause", tint = OnSurface, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MangaGroupCard(
    mangaGroup: MangaWithDownloads,
    selectedIds: List<String>,
    onToggleSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val completedChapters = mangaGroup.chapters.filter { it.status == DownloadStatus.COMPLETED }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .animateContentSize(),
    ) {
        // Manga header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(SurfaceContainer)
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = mangaGroup.manga.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(40.dp, 56.dp)
                        .clip(MaterialTheme.shapes.small),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mangaGroup.manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${completedChapters.size} CHAPTERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                "Expand",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        // Chapter list
        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            completedChapters.forEach { chapter ->
                val isSelected = chapter.id in selectedIds
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (isSelected) Primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onToggleSelect(chapter.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        "Select",
                        tint = if (isSelected) Primary else OnSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter.chapterName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${chapter.totalPages} pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String =
    when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
