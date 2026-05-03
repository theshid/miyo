package ani.saikou.sharedui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import miyo.shared_ui.generated.resources.Res
import miyo.shared_ui.generated.resources.epilogue_bold
import miyo.shared_ui.generated.resources.epilogue_extrabold
import miyo.shared_ui.generated.resources.epilogue_semibold
import miyo.shared_ui.generated.resources.hiro_misake
import miyo.shared_ui.generated.resources.instrument_serif
import miyo.shared_ui.generated.resources.inter_bold
import miyo.shared_ui.generated.resources.inter_medium
import miyo.shared_ui.generated.resources.inter_regular
import miyo.shared_ui.generated.resources.inter_semibold
import miyo.shared_ui.generated.resources.musashi
import org.jetbrains.compose.resources.Font

// ── Font Families ─────────────────────────────────────────────
// CMP's `Font(Res.font.*, ...)` is @Composable, so each family is exposed
// via a @Composable property getter. Consumers already call from a
// composable context (TextStyle / Typography construction), so the call
// sites don't change shape — only the package import does.

val Epilogue: FontFamily
    @Composable
    get() =
        FontFamily(
            Font(Res.font.epilogue_semibold, FontWeight.SemiBold),
            Font(Res.font.epilogue_bold, FontWeight.Bold),
            Font(Res.font.epilogue_extrabold, FontWeight.ExtraBold),
        )

val HiroMisake: FontFamily
    @Composable
    get() = FontFamily(Font(Res.font.hiro_misake))

val Musashi: FontFamily
    @Composable
    get() = FontFamily(Font(Res.font.musashi))

val InstrumentSerif: FontFamily
    @Composable
    get() = FontFamily(Font(Res.font.instrument_serif))

val Inter: FontFamily
    @Composable
    get() =
        FontFamily(
            Font(Res.font.inter_regular, FontWeight.Normal),
            Font(Res.font.inter_medium, FontWeight.Medium),
            Font(Res.font.inter_semibold, FontWeight.SemiBold),
            Font(Res.font.inter_bold, FontWeight.Bold),
        )

// ── Typography Scale ──────────────────────────────────────────
// Was a top-level `val SaikouTypography`; now @Composable so it can call
// the composable font-family getters. Wired into MaterialTheme(typography = …)
// from inside SaikouTheme — that's already a composable context.
@Composable
fun saikouTypography(): Typography =
    Typography(
        // Display Large — Hero character names, app title
        displayLarge =
            TextStyle(
                fontFamily = Epilogue,
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.02).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Epilogue,
                fontWeight = FontWeight.Bold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = (-0.02).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = Epilogue,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.01).sp,
            ),
        // Headline Large — Screen titles
        headlineLarge =
            TextStyle(
                fontFamily = Epilogue,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.01).sp,
            ),
        // Headline Medium — Section headers ("Trending Now", "Continue Watching")
        headlineMedium =
            TextStyle(
                fontFamily = Epilogue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Epilogue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        // Title Large — Anime/Manga titles in cards
        titleLarge =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.01.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.01.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.01.sp,
            ),
        // Body Large — Synopses, descriptions
        bodyLarge =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.02.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.01.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        // Label Large — Button text
        labelLarge =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.02.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.03.sp,
            ),
        // Label Small — Tags, genres, timestamps
        labelSmall =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.05.sp,
            ),
    )
