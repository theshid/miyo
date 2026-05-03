package ani.saikou.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ani.saikou.sharedui.theme.GhostBorder
import ani.saikou.sharedui.theme.SurfaceContainer

/**
 * Glassmorphism card following Neon Nocturne design spec:
 * - Fill: surface-container at 60% opacity
 * - Ghost Border: outline-variant at 20% opacity
 * - Rounded corners
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(SurfaceContainer.copy(alpha = 0.6f))
                .border(width = 1.dp, color = GhostBorder, shape = shape)
                .padding(contentPadding),
        content = content,
    )
}
