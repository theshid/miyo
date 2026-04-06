package ani.saikou.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.SurfaceContainer
import ani.saikou.ui.theme.SurfaceContainerHigh

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    return Brush.linearGradient(
        colors = listOf(
            SurfaceContainer,
            SurfaceContainerHigh,
            SurfaceContainer,
        ),
        start = Offset(translateAnim.value - 200f, 0f),
        end = Offset(translateAnim.value, 0f),
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(shimmerBrush()),
    )
}

/** Shimmer placeholder for the Home screen */
@Composable
fun HomeShimmer() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Avatar + greeting
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(Modifier.size(54.dp).clip(CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.width(120.dp).height(16.dp))
                ShimmerBox(Modifier.width(80.dp).height(12.dp))
            }
        }
        // Stat cards
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(Modifier.weight(1f).height(80.dp))
            ShimmerBox(Modifier.weight(1f).height(80.dp))
        }
        // Action cards
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(Modifier.weight(1f).height(56.dp))
            ShimmerBox(Modifier.weight(1f).height(56.dp))
        }
        // Section header
        ShimmerBox(Modifier.width(160.dp).height(20.dp))
        // Poster row
        PosterRowShimmer()
        // Another section
        ShimmerBox(Modifier.width(140.dp).height(20.dp))
        PosterRowShimmer()
    }
}

/** Shimmer placeholder for discovery screens */
@Composable
fun DiscoveryShimmer() {
    Column(
        modifier = Modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Search bar
        ShimmerBox(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp))
        // Carousel
        ShimmerBox(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp))
        // Chips
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerBox(Modifier.width(80.dp).height(32.dp))
            ShimmerBox(Modifier.width(70.dp).height(32.dp))
            ShimmerBox(Modifier.width(60.dp).height(32.dp))
        }
        // Section header
        ShimmerBox(Modifier.width(140.dp).height(20.dp).padding(start = 16.dp))
        // Poster row
        PosterRowShimmer()
        // Banner cards
        ShimmerBox(Modifier.width(140.dp).height(20.dp).padding(start = 16.dp))
        repeat(3) {
            ShimmerBox(Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun PosterRowShimmer() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(5) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.size(120.dp, 170.dp))
                ShimmerBox(Modifier.width(100.dp).height(12.dp))
            }
        }
    }
}
