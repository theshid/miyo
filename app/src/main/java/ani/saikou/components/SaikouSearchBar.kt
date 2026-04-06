package ani.saikou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.GhostBorder
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.SurfaceVariant

@Composable
fun SaikouSearchBar(
    modifier: Modifier = Modifier,
    placeholder: String = "Search for anything...",
    onClick: () -> Unit = {},
) {
    val shape = MaterialTheme.shapes.extraLarge

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, GhostBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = OnSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
