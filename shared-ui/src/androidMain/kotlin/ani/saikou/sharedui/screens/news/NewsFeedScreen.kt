package ani.saikou.sharedui.screens.news

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ani.saikou.domain.model.AiringScheduleItem
import ani.saikou.domain.model.NewsCategory
import ani.saikou.domain.model.NewsItem
import ani.saikou.presentation.screens.news.NewsFeedViewModel
import ani.saikou.sharedui.components.GenreChip
import ani.saikou.sharedui.components.SectionHeader
import ani.saikou.sharedui.theme.Background
import ani.saikou.sharedui.theme.OnSurface
import ani.saikou.sharedui.theme.OnSurfaceVariant
import ani.saikou.sharedui.theme.Primary
import ani.saikou.sharedui.theme.Secondary
import ani.saikou.sharedui.theme.SurfaceContainer
import ani.saikou.sharedui.theme.Tertiary
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

@Composable
fun NewsFeedScreen(
    onBack: () -> Unit,
    viewModel: NewsFeedViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        // ── Top Bar ──────────────────────────────────────────
        item {
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
                    text = "NEWS",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { /* search */ }) {
                    Icon(Icons.Default.Search, "Search", tint = OnSurface, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── Airing Schedule Section ──────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(title = "Airing Today", actionText = "VIEW ALL", onAction = {})
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Day selector
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(NewsFeedViewModel.DAY_LABELS) { index, label ->
                    val selected = index == state.selectedDayIndex
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (selected) Primary else SurfaceContainer)
                                .clickable { viewModel.selectDay(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Background else OnSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Schedule carousel
        item {
            if (state.scheduleLoading) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(state.schedule, key = { index, item -> "${item.mediaId}_$index" }) { _, item ->
                        ScheduleCard(item = item)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Latest Updates Section ───────────────────────────
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Latest Updates",
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold,
                )
                // Filter
                GenreChip(
                    text = state.sourceFilter ?: "ALL",
                    selected = true,
                    onClick = {
                        val next =
                            when (state.sourceFilter) {
                                null -> "Reddit"
                                "Reddit" -> "MAL"
                                "MAL" -> "ANN"
                                else -> null
                            }
                        viewModel.setFilter(next)
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // News list
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp)
                }
            }
        } else {
            items(state.filteredNews, key = { it.url }) { news ->
                NewsCard(
                    item = news,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(news.url)))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(item: AiringScheduleItem) {
    Column(
        modifier = Modifier.width(120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(120.dp, 160.dp)
                    .clip(MaterialTheme.shapes.medium),
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Airing time badge
            if (item.airingTime.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Primary.copy(alpha = 0.9f), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(item.airingTime, style = MaterialTheme.typography.labelSmall, color = Background)
                }
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NewsCard(
    item: NewsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(SurfaceContainer)
                .clickable(onClick = onClick)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Thumbnail
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.small),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Category + source badges
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryBadge(item.category)
                SourceBadge(item.source)
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = timeAgo(item.date),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: NewsCategory) {
    val (text, color) =
        when (category) {
            NewsCategory.EPISODE_RELEASE -> "EPISODE" to Primary
            NewsCategory.CHAPTER_RELEASE -> "CHAPTER" to Secondary
            NewsCategory.INDUSTRY_NEWS -> "NEWS" to Tertiary
            NewsCategory.DISCUSSION -> "DISCUSSION" to Secondary
            NewsCategory.SCHEDULE -> "SCHEDULE" to Primary
            NewsCategory.ANNOUNCEMENT -> "ANNOUNCE" to Tertiary
        }
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SourceBadge(source: String) {
    Box(
        modifier =
            Modifier
                .background(OnSurfaceVariant.copy(alpha = 0.1f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(source, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}

private fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hours ago"
        days < 7 -> "$days days ago"
        else -> "${days / 7} weeks ago"
    }
}
