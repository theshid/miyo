package ani.saikou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ani.saikou.domain.model.Media
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary
import ani.saikou.ui.theme.SurfaceContainer
import coil.compose.AsyncImage

/**
 * Large media card with banner, poster overlay, title, and metadata.
 * Used in Popular lists and search results.
 */
@Composable
fun MediaBannerCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SurfaceContainer)
            .clickable(onClick = onClick),
    ) {
        // Banner
        AsyncImage(
            model = media.banner ?: media.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, SurfaceContainer),
                        startY = 40f,
                    )
                ),
        )

        // Content row: poster + info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 60.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Poster thumbnail
            AsyncImage(
                model = media.cover,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp)
                    .clip(MaterialTheme.shapes.small),
            )

            // Info
            Column(
                modifier = Modifier.padding(top = 40.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = media.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    media.meanScore?.let { score ->
                        Text(
                            text = "★ ${score / 10.0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary,
                        )
                    }
                    val epText = when {
                        media.totalEpisodes != null -> "${media.totalEpisodes} eps"
                        media.totalChapters != null -> "${media.totalChapters} ch"
                        else -> null
                    }
                    if (epText != null) {
                        Text(
                            text = epText,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }
                }

                if (media.isOngoing) {
                    Box(
                        modifier = Modifier
                            .background(Secondary.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "ONGOING",
                            style = MaterialTheme.typography.labelSmall,
                            color = Secondary,
                        )
                    }
                }
            }
        }
    }
}
