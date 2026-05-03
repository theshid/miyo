package ani.saikou.sharedui.screens.login

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ani.saikou.sharedui.components.HalftoneButton
import ani.saikou.sharedui.components.HalftoneSize
import ani.saikou.sharedui.components.HalftoneVariant
import ani.saikou.sharedui.theme.HiroMisake
import ani.saikou.sharedui.theme.InstrumentSerif
import ani.saikou.sharedui.theme.Inter
import ani.saikou.sharedui.theme.Musashi
import ani.saikou.sharedui.theme.OnSurfaceVariant
import ani.saikou.sharedui.theme.Primary
import miyo.shared_ui.generated.resources.Res
import miyo.shared_ui.generated.resources.login_background
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

// Mirrored from :app/data/remote/AnilistApi.CLIENT_ID. Inlined here while
// AnilistApi still lives in :app — once the API client moves to :data and
// gets a domain-side AuthConfig, this collapses into a single source.
private const val ANILIST_CLIENT_ID = 39345

/**
 * Login screen kicks off OAuth via Custom Tabs. The success path goes
 * through [ani.saikou.LoginCallbackActivity] which relaunches the app
 * fresh from MainActivity → Splash → Home — so there's no in-process
 * onSuccess callback to wire into the navigation graph.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun LoginScreen() {
    val context = LocalContext.current

    // ── Animated shimmer for the "manga" word ──────────────────
    // Loops 0..1 over 6s linear; the gradient brush slides across
    // the text bounds via this normalized offset. CSS equivalent of
    // `background-position: 200% 0 → -200% 0`.
    val shimmerTransition = rememberInfiniteTransition(label = "mangaShimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "mangaShimmer",
    )

    // OKLCH(0.52 0.24 300) → deep purple, OKLCH(0.65 0.28 320) → magenta-purple.
    // sRGB approximations; OKLCH→sRGB is non-trivial so these are eyeballed
    // to the spec colours and can be tuned without touching the gradient logic.
    val deepPurple = Color(0xFF7028A8)
    val magentaPurple = Color(0xFFC840C8)
    // Gradient is wider than text so the bright stop sweeps across visibly;
    // TileMode.Repeated makes the off-screen seams invisible (start/end colour
    // are equal so the loop is continuous).
    val gradientLength = 800f
    val mangaBrush =
        Brush.linearGradient(
            colors = listOf(deepPurple, magentaPurple, deepPurple),
            start = Offset(shimmerOffset * gradientLength, 0f),
            end = Offset(shimmerOffset * gradientLength + gradientLength, 0f),
            tileMode = TileMode.Repeated,
        )

    // ── Slogan typography ──────────────────────────────────────
    val sloganFontSize = 64.sp
    val sloganLineHeight = 61.sp // 0.95 × fontSize per spec

    val serifSpan =
        SpanStyle(
            fontFamily = InstrumentSerif,
            fontWeight = FontWeight.Normal,
            fontSize = sloganFontSize,
            letterSpacing = (-2).sp, // -2px tightening on the serif body
        )
    val brushSpan =
        SpanStyle(
            fontFamily = Musashi,
            fontWeight = FontWeight.Normal,
            fontSize = sloganFontSize,
            // 0.05em downward nudge — brush glyphs sit visually slightly higher
            // than the serif baseline; this re-aligns them inline.
            baselineShift = BaselineShift(-0.05f),
        )
    val mangaSpan = brushSpan.copy(brush = mangaBrush)

    val slogan =
        buildAnnotatedString {
            withStyle(serifSpan) { append("Every ") }
            withStyle(brushSpan) { append("anime") }
            withStyle(serifSpan) { append(".\n") }

            withStyle(serifSpan) { append("Every ") }
            withStyle(mangaSpan) { append("manga") }
            withStyle(serifSpan) { append(".\n") }

            withStyle(brushSpan) { append("One library") }
            withStyle(serifSpan) { append(".") }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image — fills the whole screen behind the content
        Image(
            painter = painterResource(Res.drawable.login_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Dark scrim so the white slogan stays readable over any image colour
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 56.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── MIYO logo ─────────────────────────────────────
            Text(
                text = "MIYO",
                style =
                    MaterialTheme.typography.displayLarge.copy(
                        fontFamily = HiroMisake,
                        fontSize = 96.sp,
                    ),
                color = Primary,
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Slogan (focal element) ────────────────────────
            Text(
                text = slogan,
                style =
                    TextStyle(
                        color = Color.White,
                        lineHeight = sloganLineHeight,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    ),
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Login button ──────────────────────────────────
            HalftoneButton(
                text = "LOGIN WITH ANILIST",
                onClick = {
                    val url = "https://anilist.co/api/v2/oauth/authorize?client_id=$ANILIST_CLIENT_ID&response_type=token"
                    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                },
                modifier = Modifier.fillMaxWidth(),
                size = HalftoneSize.LG,
                variant = HalftoneVariant.PURPLE,
                geistFamily = Inter,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Social links row ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { /* Discord */ }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Discord",
                        tint = OnSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { /* GitHub */ }) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "GitHub",
                        tint = OnSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { /* Telegram */ }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Telegram",
                        tint = OnSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
@Suppress("UnusedPrivateMember") // Compose @Preview targets — surfaced by Android Studio's preview tooling.
private fun LoginScreenPreview() {
    LoginScreen()
}
