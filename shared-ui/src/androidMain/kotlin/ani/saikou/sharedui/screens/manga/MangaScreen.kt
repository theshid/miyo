package ani.saikou.sharedui.screens.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ani.saikou.presentation.screens.manga.MangaViewModel
import ani.saikou.sharedui.components.DiscoveryShimmer
import ani.saikou.sharedui.components.GenreChip
import ani.saikou.sharedui.components.LoadErrorBanner
import ani.saikou.sharedui.components.MediaBannerCard
import ani.saikou.sharedui.components.MediaPosterCard
import ani.saikou.sharedui.components.SaikouSearchBar
import ani.saikou.sharedui.components.ScrollToTopFab
import ani.saikou.sharedui.components.SectionHeader
import ani.saikou.sharedui.components.TrendingCarousel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun MangaScreen(
    onNavigateToMedia: (Int) -> Unit,
    onNavigateToSearch: (genre: String?, sort: String?) -> Unit,
    viewModel: MangaViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        DiscoveryShimmer()
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
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
                    placeholder = "Search manga...",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // ── Load failure banner ──────────────────────────────
            state.error?.let { msg ->
                item {
                    LoadErrorBanner(
                        message = msg,
                        onRetry = { viewModel.loadMangaData() },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // ── Trending Manga Carousel ──────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = "Trending Manga",
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
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        GenreChip(
                            text = "ALL MANGA",
                            selected = true,
                            onClick = { onNavigateToSearch(null, "POPULARITY_DESC") },
                        )
                    }
                    item { GenreChip(text = "TOP RATED", onClick = { onNavigateToSearch(null, "SCORE_DESC") }) }
                    item { GenreChip(text = "ACTION", onClick = { onNavigateToSearch("Action", null) }) }
                    item { GenreChip(text = "ROMANCE", onClick = { onNavigateToSearch("Romance", null) }) }
                    item { GenreChip(text = "FANTASY", onClick = { onNavigateToSearch("Fantasy", null) }) }
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
                                    subtitle = if (media.totalChapters != null) "${media.totalChapters} Ch" else "Ongoing",
                                    onClick = { onNavigateToMedia(media.id) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Popular Manga ────────────────────────────────────
            item {
                SectionHeader(
                    title = "Popular Manga",
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

                if (index == state.popular.lastIndex - 2) {
                    viewModel.loadMorePopular()
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        ScrollToTopFab(
            visible = showFab,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
