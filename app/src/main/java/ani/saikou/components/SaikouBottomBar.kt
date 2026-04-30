package ani.saikou.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ani.saikou.navigation.Screen
import ani.saikou.navigation.bottomBarScreens
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.Primary

/**
 * Bottom navigation bar with a gradient "pill" indicator that slides between
 * slots on a slightly-bouncy spring. Inspired by SmoothBottomBar — adapted to
 * Miyo's dark surface + electric-purple palette.
 *
 * Idle items show only their icon (muted tint). The selected item shows its
 * icon + label inside the animated pill. Label enters via horizontal expand
 * + fade so the motion reads as a single fluid transition.
 */
@Composable
fun SaikouBottomBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = bottomBarScreens
    val selectedIndex =
        items
            .indexOfFirst { it.route == currentRoute }
            .takeIf { it >= 0 } ?: 0

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Primary)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val slotWidth = maxWidth / items.size
            val pillInset = 6.dp
            val pillWidth = slotWidth - pillInset * 2

            // Spring the pill left/right between slots. Low bouncy + medium
            // stiffness gives the "pop" feel expressive anime UIs use.
            val pillOffset by animateDpAsState(
                targetValue = slotWidth * selectedIndex + pillInset,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                label = "pill_offset",
            )

            // Dark pill, underneath the item row so icons sit on top of it.
            Box(
                modifier =
                    Modifier
                        .offset(x = pillOffset)
                        .width(pillWidth)
                        .height(48.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(24.dp),
                        ).clip(RoundedCornerShape(24.dp))
                        .background(Background),
            )

            // Items row — each slot takes equal width so slotWidth math lines up.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, screen ->
                    BottomBarItem(
                        screen = screen,
                        selected = index == selectedIndex,
                        onClick = { onNavigate(screen) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Icon pops slightly on selection — ~8% scale lift + 100ms tween keeps
    // the reaction snappy without competing with the pill's spring.
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "icon_scale",
    )

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                ).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        screen.icon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = screen.label,
                // Idle icons sit on the purple bar at reduced opacity so the
                // selected white-on-black icon is the clear focal point.
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                modifier =
                    Modifier
                        .size(22.dp)
                        .scale(iconScale),
            )
        }
        // Label only visible in the selected state — expands horizontally
        // from zero width with a fade, so the overall motion reads as the
        // pill "growing" its label rather than a pop-in.
        AnimatedVisibility(
            visible = selected,
            enter =
                expandHorizontally(animationSpec = tween(220)) +
                    fadeIn(animationSpec = tween(220)),
            exit =
                shrinkHorizontally(animationSpec = tween(160)) +
                    fadeOut(animationSpec = tween(120)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = screen.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
