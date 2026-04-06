package ani.saikou.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.components.GenreChip
import ani.saikou.components.MediaBannerCard
import ani.saikou.domain.model.Media
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.GhostBorder
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.SurfaceContainer
import ani.saikou.ui.theme.SurfaceVariant
import coil.compose.AsyncImage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToMedia: (Int) -> Unit,
    initialGenre: String? = null,
    initialType: String? = null,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Apply initial filters once
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (initialGenre != null) viewModel.toggleGenre(initialGenre)
        if (initialType != null) viewModel.updateType(initialType)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // ── Top Bar: Back + Search Input ─────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
            }
            SearchInput(
                query = state.query,
                onQueryChange = viewModel::updateQuery,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Filter Dropdowns ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterDropdown(
                label = "Genre",
                options = SearchViewModel.GENRES,
                onSelect = { viewModel.toggleGenre(it) },
            )
            FilterDropdown(
                label = "Sort",
                options = SearchViewModel.SORT_OPTIONS.keys.toList(),
                onSelect = { viewModel.updateSort(SearchViewModel.SORT_OPTIONS[it]) },
            )
            FilterDropdown(
                label = "Format",
                options = listOf("ANIME", "MANGA"),
                onSelect = { viewModel.updateType(it) },
            )
        }

        // ── Active Filter Chips ──────────────────────────────
        if (state.selectedGenres.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.selectedGenres.forEach { genre ->
                    GenreChip(
                        text = genre,
                        selected = true,
                        onClick = { viewModel.toggleGenre(genre) },
                    )
                }
            }
        }

        // ── Results Header ───────────────────────────────────
        if (state.results.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Search Results",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${state.totalFound} Items Found",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                }
                Row {
                    IconButton(onClick = { if (!state.isGridView) viewModel.toggleGridView() }) {
                        Icon(
                            Icons.Default.GridView, "Grid",
                            tint = if (state.isGridView) Primary else OnSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { if (state.isGridView) viewModel.toggleGridView() }) {
                        Icon(
                            Icons.Default.ViewList, "List",
                            tint = if (!state.isGridView) Primary else OnSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // ── Results ──────────────────────────────────────────
        if (state.isLoading && state.results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
            }
        } else if (state.isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = state.results,
                    key = { _, media -> media.id },
                ) { index, media ->
                    SearchGridCard(media = media, onClick = { onNavigateToMedia(media.id) })
                    if (index == state.results.lastIndex - 2) viewModel.loadMore()
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items = state.results,
                    key = { _, media -> media.id },
                ) { index, media ->
                    MediaBannerCard(media = media, onClick = { onNavigateToMedia(media.id) })
                    if (index == state.results.lastIndex - 2) viewModel.loadMore()
                }
            }
        }
    }
}

@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraLarge

    Row(
        modifier = modifier
            .clip(shape)
            .background(SurfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, GhostBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search, "Search",
            tint = OnSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
            cursorBrush = SolidColor(Primary),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        "Search for anything...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                innerTextField()
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Close, "Clear",
                tint = OnSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.extraSmall

    Box {
        Text(
            text = "$label ▾",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
            modifier = Modifier
                .clip(shape)
                .background(SurfaceVariant.copy(alpha = 0.3f))
                .border(1.dp, GhostBorder, shape)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceContainer),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(option, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchGridCard(
    media: Media,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Cover with score badge and format tag
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.medium),
        ) {
            AsyncImage(
                model = media.cover,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Score badge top-left
            if (media.meanScore != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Primary.copy(alpha = 0.85f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "★ ${media.meanScore / 10.0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurface,
                    )
                }
            }
            // Format badge bottom-right
            media.format?.let { format ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(SurfaceContainer.copy(alpha = 0.85f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = format,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurface,
                    )
                }
            }
        }

        Text(
            text = media.displayTitle,
            style = MaterialTheme.typography.titleSmall,
            color = OnSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (!media.genres.isNullOrEmpty()) {
            Text(
                text = media.genres.take(2).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
