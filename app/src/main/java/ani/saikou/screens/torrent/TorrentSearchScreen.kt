package ani.saikou.screens.torrent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.components.GenreChip
import ani.saikou.components.PillButton
import ani.saikou.domain.model.TorrentResult
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.GhostBorder
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary
import ani.saikou.ui.theme.SurfaceBright
import ani.saikou.ui.theme.SurfaceContainer
import ani.saikou.ui.theme.SurfaceVariant
import ani.saikou.ui.theme.Tertiary

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TorrentSearchScreen(
    onBack: () -> Unit,
    viewModel: TorrentSearchViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background),
    ) {
        // ── Top Bar ──────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
            }
            Text(
                text = "Anime Torrent Search",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { /* global search */ }) {
                Icon(Icons.Default.Search, "Search", tint = OnSurface, modifier = Modifier.size(20.dp))
            }
        }

        // ── Search Input ─────────────────────────────────────
        SearchInput(
            query = state.query,
            onQueryChange = viewModel::updateQuery,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Filter Chips ─────────────────────────────────────
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Source filters
            GenreChip(
                text = "ALL SOURCES",
                selected = state.sourceFilter == null,
                onClick = { viewModel.setSourceFilter(null) },
            )
            listOf("NYAA", "BTDIGG", "ANIDEX").forEach { src ->
                GenreChip(
                    text = src,
                    selected = state.sourceFilter == src,
                    onClick = { viewModel.setSourceFilter(if (state.sourceFilter == src) null else src) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quality + sort row
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("1080p", "720p", "480p").forEach { q ->
                GenreChip(
                    text = q,
                    selected = state.qualityFilter == q,
                    onClick = { viewModel.setQualityFilter(if (state.qualityFilter == q) null else q) },
                )
            }

            // Sort chips
            GenreChip(text = "Shuffle", selected = false) // Decorative divider
            GenreChip(
                text = "Sort: ${state.sortBy.name.lowercase().replaceFirstChar { it.uppercase() }}",
                selected = true,
                onClick = {
                    val next =
                        when (state.sortBy) {
                            SortOption.SEEDERS -> SortOption.SIZE
                            SortOption.SIZE -> SortOption.DATE
                            SortOption.DATE -> SortOption.SEEDERS
                        }
                    viewModel.setSortBy(next)
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Content ──────────────────────────────────────────
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
                }
            }

            state.results.isEmpty() && state.query.isNotBlank() && !state.isLoading -> {
                EmptyState(onRetry = { viewModel.retry() })
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 80.dp,
                        ),
                ) {
                    items(items = state.results, key = { it.magnetLink.hashCode() }) { result ->
                        TorrentCard(
                            result = result,
                            onClick = { viewModel.selectResult(result) },
                        )
                    }

                    // Disclaimer
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SEARCH RESULTS ONLY. REQUIRES EXTERNAL TORRENT CLIENT.",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    // ── Detail Bottom Sheet ──────────────────────────────────
    state.selectedResult?.let { result ->
        TorrentDetailSheet(
            result = result,
            onDismiss = { viewModel.clearSelection() },
            onOpenMagnet = { openMagnet(context, result.magnetLink) },
            onCopyLink = { copyMagnet(context, result.magnetLink) },
        )
    }
}

@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SurfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, GhostBorder, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, "Search", tint = OnSurfaceVariant.copy(0.6f), modifier = Modifier.size(20.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
            cursorBrush = SolidColor(Primary),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Chainsaw Man", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant.copy(0.4f))
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                "Clear",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp).clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun TorrentCard(
    result: TorrentResult,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(SurfaceContainer)
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Title
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleSmall,
            color = OnSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Quality badges
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            result.quality?.resolution?.let { QualityBadge(it) }
            result.quality?.videoSource?.let { QualityBadge(it) }
            result.quality?.codec?.let { QualityBadge(it) }
            if (result.quality?.isBatch == true) QualityBadge("Batch")
        }

        // Meta row: source, size, seeders/leechers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Source badge
            SourceBadge(result.source)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Size
                Text(result.size, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)

                // Seeders
                if (result.seeders > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.Upload, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                        Text("${result.seeders}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                    }
                }

                // Leechers
                if (result.leechers > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.Download, null, tint = Primary, modifier = Modifier.size(14.dp))
                        Text("${result.leechers}", style = MaterialTheme.typography.labelSmall, color = Primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityBadge(text: String) {
    Box(
        modifier =
            Modifier
                .background(Primary.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Primary)
    }
}

@Composable
private fun SourceBadge(source: String) {
    val color =
        when (source) {
            "NYAA" -> Secondary
            "BTDIGG" -> Tertiary
            "ANIDEX" -> Color(0xFF4CAF50)
            else -> OnSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(source, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(onRetry: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon
        Box(
            modifier =
                Modifier
                    .size(80.dp)
                    .background(SurfaceContainer, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Text("⊕❩", style = MaterialTheme.typography.headlineMedium, color = Primary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("No torrents found", style = MaterialTheme.typography.titleLarge, color = OnSurface, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Try a different search term or check your connection. We couldn't find any results matching your query.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GenreChip(text = "⏱ RECENT", onClick = {})
            GenreChip(text = "↻ RETRY", selected = true, onClick = onRetry)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Disclaimer
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, Primary.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("NOTICE", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
            Text(
                "Saikou does not host any content. All search results are fetched from public third-party indexers. Please ensure you are compliant with local regulations.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TorrentDetailSheet(
    result: TorrentResult,
    onDismiss: () -> Unit,
    onOpenMagnet: () -> Unit,
    onCopyLink: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = SurfaceBright,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.Bold,
            )

            // Meta row
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetaItem(icon = "💾", value = result.size)
                if (result.seeders > 0) MetaItem(icon = "↑", value = "${result.seeders}", color = Color(0xFF4CAF50))
                if (result.leechers > 0) MetaItem(icon = "↓", value = "${result.leechers}", color = Primary)
            }

            if (result.date.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📅", style = MaterialTheme.typography.bodySmall)
                    Text(result.date, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            }

            // Quality badges
            result.quality?.let { quality ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quality.resolution?.let { QualityBadge(it) }
                    quality.videoSource?.let { QualityBadge(it) }
                    quality.codec?.let { QualityBadge(it) }
                    if (quality.isBatch) QualityBadge("Batch")
                }
            }

            HorizontalDivider(color = GhostBorder)

            // Actions
            PillButton(
                text = "Open Magnet",
                onClick = {
                    onOpenMagnet()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Copy link — outlined style
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .border(1.dp, OnSurfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.extraLarge)
                        .clickable {
                            onCopyLink()
                            onDismiss()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = OnSurface, modifier = Modifier.size(18.dp))
                    Text("Copy Link", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetaItem(
    icon: String,
    value: String,
    color: Color = OnSurfaceVariant,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
    }
}

private fun openMagnet(
    context: Context,
    magnetLink: String,
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetLink))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No torrent client installed", Toast.LENGTH_SHORT).show()
    }
}

private fun copyMagnet(
    context: Context,
    magnetLink: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Magnet Link", magnetLink))
    Toast.makeText(context, "Magnet link copied", Toast.LENGTH_SHORT).show()
}
