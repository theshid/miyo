package ani.saikou.screens.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.components.GenreChip
import ani.saikou.components.MediaBannerCard
import ani.saikou.components.MediaPosterCard
import ani.saikou.components.SaikouSearchBar
import ani.saikou.components.SectionHeader
import ani.saikou.components.TrendingCarousel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AnimeScreen(
    onNavigateToMedia: (Int) -> Unit,
    onNavigateToSearch: (genre: String?, sort: String?) -> Unit,
    viewModel: AnimeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        ani.saikou.components.DiscoveryShimmer()
        return
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── Search bar ───────────────────────────────────────
        item {
            SaikouSearchBar(
                onClick = { onNavigateToSearch(null, null) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // ── Load failure banner ──────────────────────────────
        state.error?.let { msg ->
            item {
                ani.saikou.components.LoadErrorBanner(
                    message = msg,
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ── Trending Carousel (full-bleed hero) ──────────────
        item {
            TrendingCarousel(
                items = state.trending,
                onItemClick = onNavigateToMedia,
            )
        }

        // ── Quick chips ──────────────────────────────────────
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GenreChip(
                    text = "ALL ANIME",
                    selected = true,
                    onClick = { onNavigateToSearch(null, "POPULARITY_DESC") },
                )
                GenreChip(text = "ACTION", onClick = { onNavigateToSearch("Action", null) })
                GenreChip(text = "ROMANCE", onClick = { onNavigateToSearch("Romance", null) })
                GenreChip(text = "SCI-FI", onClick = { onNavigateToSearch("Sci-Fi", null) })
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
