package ani.saikou.components

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.saikou.R
import ani.saikou.sharedui.theme.Primary
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import miyo.shared_ui.generated.resources.Res
import miyo.shared_ui.generated.resources.bangers
import org.jetbrains.compose.resources.Font

// CMP's Font(Res.font.*) is @Composable, so the family is a composable getter.
private val Bangers: FontFamily
    @Composable
    get() = FontFamily(Font(Res.font.bangers))

/**
 * Lottie-based loading indicator with anime-style text.
 * - Video player: snail animation
 * - Manga reader: cat animation
 */
@Composable
fun LottieLoader(
    @RawRes animationRes: Int,
    message: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animationRes))

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(size),
            alignment = Alignment.Center,
        )
        if (message != null) {
            Text(
                text = message,
                fontFamily = Bangers,
                fontSize = 16.sp,
                color = Primary,
                letterSpacing = 1.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Video loader — for video player loading states.
 * The animation includes its own "Loading" text, so no message is needed.
 */
@Composable
fun VideoLoader(
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
) {
    LottieLoader(
        animationRes = R.raw.video_loader,
        message = null,
        modifier = modifier,
        size = size,
    )
}

/**
 * Cat loader — for manga reader loading states.
 */
@Composable
fun CatLoader(
    message: String? = "Loading chapter...",
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    LottieLoader(
        animationRes = R.raw.cat_loader,
        message = message,
        modifier = modifier,
        size = size,
    )
}
