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
import ani.saikou.sharedui.theme.SurfaceContainer
import ani.saikou.sharedui.theme.SurfaceContainerHigh

@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmer_translate",
        )
    return Brush.linearGradient(
        colors =
            listOf(
                SurfaceContainer,
                SurfaceContainerHigh,
                SurfaceContainer,
            ),
        start = Offset(translateAnim.value - 200f, 0f),
        end = Offset(translateAnim.value, 0f),
    )
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .background(shimmerBrush()),
    )
}

/** Shimmer placeholder for the Home screen — mirrors the real layout: header,
 *  stats, activity heatmap, action grid, then a couple of poster rows. */
@Composable
fun HomeShimmer() {
    Column(
        modifier = Modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Header: avatar + greeting + 3 action icons on the right
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                ShimmerBox(Modifier.size(48.dp).clip(CircleShape))
                ShimmerBox(Modifier.width(140.dp).height(22.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { ShimmerBox(Modifier.size(28.dp).clip(CircleShape)) }
            }
        }

        // Stats cards
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBox(Modifier.weight(1f).height(80.dp))
            ShimmerBox(Modifier.weight(1f).height(80.dp))
        }

        // Activity heatmap — month nav + 6-row grid is roughly 220dp tall
        ShimmerBox(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(220.dp),
        )

        // Quick action grid: 3 paired rows + 1 full-width
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShimmerBox(Modifier.weight(1f).height(64.dp))
                    ShimmerBox(Modifier.weight(1f).height(64.dp))
                }
            }
            ShimmerBox(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(64.dp))
        }

        // Airing Soon
        SectionShimmer(headerWidth = 120.dp)
        // Continue Watching (with SEE ALL hint)
        SectionShimmer(headerWidth = 180.dp, hasSeeAll = true)
        // Continue Reading
        SectionShimmer(headerWidth = 170.dp, hasSeeAll = true)
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

/** Section header (with optional "SEE ALL" hint) + a poster row underneath. */
@Composable
private fun SectionShimmer(
    headerWidth: androidx.compose.ui.unit.Dp,
    hasSeeAll: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            ShimmerBox(Modifier.width(headerWidth).height(20.dp))
            if (hasSeeAll) ShimmerBox(Modifier.width(56.dp).height(14.dp))
        }
        PosterRowShimmer()
    }
}
