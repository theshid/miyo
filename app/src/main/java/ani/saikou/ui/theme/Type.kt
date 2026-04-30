package ani.saikou.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ani.saikou.R

// ── Font Families ─────────────────────────────────────────────
val Epilogue =
    FontFamily(
        Font(R.font.epilogue_semibold, FontWeight.SemiBold),
        Font(R.font.epilogue_bold, FontWeight.Bold),
        Font(R.font.epilogue_extrabold, FontWeight.ExtraBold),
    )

val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

// ── Typography Scale ──────────────────────────────────────────
val SaikouTypography =
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
        // Display Medium
        displayMedium =
            TextStyle(
                fontFamily = Epilogue,
                fontWeight = FontWeight.Bold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = (-0.02).sp,
            ),
        // Display Small
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
        // Headline Small
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
        // Title Medium
        titleMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.01.sp,
            ),
        // Title Small
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
        // Body Medium — Metadata, secondary info
        bodyMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.01.sp,
            ),
        // Body Small
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
        // Label Medium
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
