package ani.saikou.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ani.saikou.sharedui.theme.OnSurface
import ani.saikou.sharedui.theme.OnSurfaceVariant
import coil3.compose.AsyncImage

/**
 * Compact poster card (120x170) used in carousels:
 * Continue Watching, Continue Reading, Recommendations, etc.
 */
@Composable
fun MediaPosterCard(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .width(120.dp)
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(120.dp, 170.dp)
                    .clip(MaterialTheme.shapes.medium),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
