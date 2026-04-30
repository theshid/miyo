package ani.saikou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.SurfaceContainerHigh

/**
 * Inline banner shown when a screen's data load fails. Pairs with a Retry
 * button so users have a way out without restarting the app. Used by
 * Anime/Manga/Home where the API silently returning empty would otherwise
 * leave the screen blank.
 */
@Composable
fun LoadErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(SurfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = OnSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Couldn't load",
                style = MaterialTheme.typography.titleSmall,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Retry",
                tint = Primary,
            )
            Text(
                "Retry",
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
