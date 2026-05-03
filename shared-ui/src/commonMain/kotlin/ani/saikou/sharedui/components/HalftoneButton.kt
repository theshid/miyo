package ani.saikou.sharedui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class HalftoneSize(
    val font: TextUnit,
    val padH: Dp,
    val padV: Dp,
    val dotGrid: Dp,
    val letterSp: TextUnit,
) {
    SM(12.sp, 22.dp, 11.dp, 5.dp, 1.2.sp),
    MD(14.sp, 20.dp, 14.dp, 6.dp, 1.4.sp),
    LG(16.sp, 36.dp, 17.dp, 7.dp, 1.6.sp),
    XL(18.sp, 44.dp, 20.dp, 8.dp, 1.8.sp),
}

enum class HalftoneVariant(
    val bg: Color,
    val dot: Color,
    val fg: Color,
) {
    PURPLE(Color(0xFF7E3CCC), Color(0x73FFFFFF), Color.White),
    INK(Color(0xFF0A0410), Color(0x997E3CCC), Color.White),
    CREAM(Color(0xFFF4ECD8), Color(0x730A0410), Color(0xFF0A0410)),
}

private val INK = Color(0xFF0A0410)

@Composable
fun HalftoneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: HalftoneSize = HalftoneSize.LG,
    variant: HalftoneVariant = HalftoneVariant.PURPLE,
    enabled: Boolean = true,
    geistFamily: FontFamily = FontFamily.SansSerif,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressOffset by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "press",
    )
    val shadowOffset by animateDpAsState(
        targetValue = if (pressed) 1.dp else 4.dp,
        animationSpec = tween(140),
        label = "shadow",
    )
    val dotShift by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        label = "dots",
    )

    Box(
        modifier =
            modifier
                .alpha(if (enabled) 1f else 0.4f)
                .offset(x = pressOffset, y = pressOffset)
                // Hard-offset shadow (no blur). this.size is the DrawScope's
                // canvas size — explicit `this` to disambiguate from the outer
                // `size: HalftoneSize` parameter.
                .drawBehind {
                    drawRoundRect(
                        color = INK,
                        topLeft = Offset(shadowOffset.toPx(), shadowOffset.toPx()),
                        size = this.size,
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }.clip(RoundedCornerShape(4.dp))
                .background(variant.bg)
                .border(2.5.dp, INK, RoundedCornerShape(4.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ).drawWithContent {
                    drawContent()
                    // Halftone dot overlay
                    val grid = size.dotGrid.toPx()
                    val r = 1.2.dp.toPx()
                    val sx = dotShift.toPx()
                    var y = sx
                    while (y < this.size.height) {
                        var x = sx
                        while (x < this.size.width) {
                            drawCircle(variant.dot, r, Offset(x, y), blendMode = BlendMode.Overlay)
                            x += grid
                        }
                        y += grid
                    }
                    // Top sheen + bottom shade
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.22f),
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.18f),
                            ),
                    )
                }.padding(horizontal = size.padH, vertical = size.padV),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style =
                TextStyle(
                    fontFamily = geistFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = size.font,
                    letterSpacing = size.letterSp,
                    color = variant.fg,
                ),
        )
    }
}
