package ani.saikou.sharedui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ani.saikou.sharedui.theme.OnSurface
import ani.saikou.sharedui.theme.SurfaceContainerHigh

/**
 * Small scroll-to-top affordance shown over a scrollable surface. Slides up
 * and fades in once [`visible`] flips true (typically when the underlying
 * list scrolls past the first item) and disappears when there's nothing to
 * scroll back to.
 *
 * Place inside a [`Box`] aligned to the bottom-end of the scrollable area.
 */
@Composable
fun ScrollToTopFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = SurfaceContainerHigh.copy(alpha = 0.92f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier =
                Modifier
                    .padding(16.dp)
                    .size(44.dp)
                    .clickable(onClick = onClick),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Scroll to top",
                tint = OnSurface,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
