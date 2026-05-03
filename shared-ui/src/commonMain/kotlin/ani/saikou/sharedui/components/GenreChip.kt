package ani.saikou.sharedui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ani.saikou.sharedui.theme.GhostBorder
import ani.saikou.sharedui.theme.OnSurface
import ani.saikou.sharedui.theme.SurfaceVariant

@Composable
fun GenreChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val shape = MaterialTheme.shapes.extraSmall
    val bgColor =
        if (selected) {
            ani.saikou.sharedui.theme.Primary
                .copy(alpha = 0.15f)
        } else {
            SurfaceVariant.copy(alpha = 0.5f)
        }
    val borderColor =
        if (selected) {
            ani.saikou.sharedui.theme.Primary
                .copy(alpha = 0.5f)
        } else {
            GhostBorder
        }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) ani.saikou.sharedui.theme.Primary else OnSurface,
        modifier =
            modifier
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
