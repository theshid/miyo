package ani.saikou.screens.anime

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.components.GenreChip
import ani.saikou.components.MediaBannerCard
import ani.saikou.components.MediaPosterCard
import ani.saikou.components.SaikouSearchBar
import ani.saikou.components.SectionHeader
import ani.saikou.components.TrendingCarousel
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.Primary

@Composable
fun AnimeScreen(
    onNavigateToMedia: (Int) -> Unit,
    onNavigateToSearch: (genre: String?) -> Unit,
    viewModel: AnimeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        ani.saikou.components.DiscoveryShimmer()
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── Search bar ───────────────────────────────────────
        item {
            SaikouSearchBar(
                onClick = { onNavigateToSearch(null) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // ── Trending Carousel ────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = "Trending Now",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TrendingCarousel(
                    items = state.trending,
                    onItemClick = onNavigateToMedia,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ── Quick chips ──────────────────────────────────────
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GenreChip(text = "ALL ANIME", selected = true, onClick = { onNavigateToSearch(null) })
                GenreChip(text = "ACTION", onClick = { onNavigateToSearch("Action") })
                GenreChip(text = "ROMANCE", onClick = { onNavigateToSearch("Romance") })
                GenreChip(text = "SCI-FI", onClick = { onNavigateToSearch("Sci-Fi") })
            }
        }

        // ── Recently Updated ─────────────────────────────────
        if (state.recentlyUpdated.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Recently Updated",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = state.recentlyUpdated,
                            key = { it.id },
                        ) { media ->
                            MediaPosterCard(
                                title = media.displayTitle,
                                coverUrl = media.cover,
                                subtitle = if (media.nextAiringEpisode != null) "Ep ${media.nextAiringEpisode}" else null,
                                onClick = { onNavigateToMedia(media.id) },
                            )
                        }
                    }
                }
            }
        }

        // ── Popular This Season ──────────────────────────────
        item {
            SectionHeader(
                title = "Popular This Season",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        itemsIndexed(
            items = state.popular,
            key = { _, media -> media.id },
        ) { index, media ->
            MediaBannerCard(
                media = media,
                onClick = { onNavigateToMedia(media.id) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Load more when near end
            if (index == state.popular.lastIndex - 2) {
                viewModel.loadMorePopular()
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
