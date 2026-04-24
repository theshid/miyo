package ani.saikou.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.SurfaceContainerHigh

enum class TourTarget {
    STATS,
    ACTIVITY,
    ACTIONS,
    AIRING,
    CONTINUE_WATCHING,
}

data class TourStep(
    val target: TourTarget,
    val title: String,
    val description: String,
)

val DefaultHomeTourSteps = listOf(
    TourStep(
        target = TourTarget.STATS,
        title = "Your stats",
        description = "Episodes you've watched and chapters you've read locally — counted as you go.",
    ),
    TourStep(
        target = TourTarget.ACTIVITY,
        title = "Activity calendar",
        description = "Every day you read or watch lights up. Days with airing episodes show the cover — tap to see what's coming.",
    ),
    TourStep(
        target = TourTarget.ACTIONS,
        title = "Quick actions",
        description = "Jump to your anime list, manga list, news, downloads, the calendar and more.",
    ),
    TourStep(
        target = TourTarget.AIRING,
        title = "Airing soon",
        description = "Upcoming episodes for shows on your CURRENT list, sorted by what's airing next.",
    ),
    TourStep(
        target = TourTarget.CONTINUE_WATCHING,
        title = "Continue watching",
        description = "Pick up the show or chapter you last left off — synced from your watch and read history.",
    ),
)

class TourState {
    /**
     * Bounds per target in root (window-relative) coordinates. The overlay
     * subtracts its own root position before drawing so spotlights align
     * even when the screen sits inside a Scaffold or NavHost with insets.
     */
    val bounds = mutableStateMapOf<TourTarget, Rect>()
}

@Composable
fun rememberTourState(): TourState = remember { TourState() }

fun Modifier.tourTarget(state: TourState, target: TourTarget): Modifier =
    this.onGloballyPositioned { c ->
        if (c.isAttached) state.bounds[target] = c.boundsInRoot()
    }

@Composable
fun TourOverlay(
    steps: List<TourStep>,
    state: TourState,
    currentStep: Int,
    onStepChanged: (Int) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = steps.getOrNull(currentStep) ?: run {
        onComplete()
        return
    }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val rootBounds = state.bounds[step.target]
    val targetBounds: Rect? = rootBounds?.translate(-overlayOrigin.x, -overlayOrigin.y)

    val cardAtTop = targetBounds != null && targetBounds.top > screenHeightPx * 0.55f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        if (targetBounds != null && targetBounds.width > 0 && targetBounds.height > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dim = Color.Black.copy(alpha = 0.78f)
                val pad = 8.dp.toPx()
                val tl = Offset(
                    (targetBounds.left - pad).coerceAtLeast(0f),
                    (targetBounds.top - pad).coerceAtLeast(0f),
                )
                val br = Offset(
                    (targetBounds.right + pad).coerceAtMost(size.width),
                    (targetBounds.bottom + pad).coerceAtMost(size.height),
                )
                if (tl.y > 0f) {
                    drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, tl.y))
                }
                if (br.y < size.height) {
                    drawRect(dim, topLeft = Offset(0f, br.y), size = Size(size.width, size.height - br.y))
                }
                if (tl.x > 0f) {
                    drawRect(dim, topLeft = Offset(0f, tl.y), size = Size(tl.x, br.y - tl.y))
                }
                if (br.x < size.width) {
                    drawRect(dim, topLeft = Offset(br.x, tl.y), size = Size(size.width - br.x, br.y - tl.y))
                }
                drawRoundRect(
                    color = Primary,
                    topLeft = tl,
                    size = Size(br.x - tl.x, br.y - tl.y),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f)),
            )
        }

        Box(
            modifier = Modifier
                .align(if (cardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(top = if (cardAtTop) 32.dp else 0.dp)
                .padding(bottom = if (cardAtTop) 0.dp else 32.dp),
        ) {
            TooltipCard(
                step = step,
                stepNumber = currentStep + 1,
                totalSteps = steps.size,
                onSkip = onComplete,
                onNext = {
                    if (currentStep >= steps.lastIndex) onComplete()
                    else onStepChanged(currentStep + 1)
                },
            )
        }
    }
}

@Composable
private fun TooltipCard(
    step: TourStep,
    stepNumber: Int,
    totalSteps: Int,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerHigh)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = step.title,
            style = MaterialTheme.typography.titleMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
        )
        Spacer(Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$stepNumber / $totalSteps",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Primary)
                    .clickable(onClick = onNext)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (stepNumber >= totalSteps) "Done" else "Next",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
