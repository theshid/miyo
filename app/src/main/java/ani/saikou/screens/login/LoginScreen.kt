package ani.saikou.screens.login

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ani.saikou.R
import ani.saikou.components.PillButton
import ani.saikou.data.remote.AnilistApi
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.Epilogue
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.Secondary

/**
 * Login screen kicks off OAuth via Custom Tabs. The success path goes
 * through [ani.saikou.LoginCallbackActivity] which relaunches the app
 * fresh from MainActivity → Splash → Home — so there's no in-process
 * onSuccess callback to wire into the navigation graph.
 */
@Composable
fun LoginScreen() {
    val context = LocalContext.current

    // Subtle diagonal gradient glow
    val gradientBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    Background,
                    Primary.copy(alpha = 0.06f),
                    Secondary.copy(alpha = 0.04f),
                    Background,
                ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background)
                .drawBehind { drawRect(gradientBrush) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            // Samurai hero image
            Image(
                painter = painterResource(R.drawable.samurai_login),
                contentDescription = "Miyo",
                modifier =
                    Modifier
                        .fillMaxWidth(0.75f)
                        .heightIn(max = 360.dp),
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App name — thin weight, large display
            Text(
                text = "MIYO",
                style =
                    MaterialTheme.typography.displayLarge.copy(
                        fontFamily = Epilogue,
                        fontWeight = FontWeight.W300,
                        letterSpacing = 4.dp.value.sp,
                    ),
                color = Primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Every anime. Every manga.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "One Library.",
                style = MaterialTheme.typography.headlineMedium,
                color = Primary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Login button — launches AniList OAuth in Custom Tab
            PillButton(
                text = "LOGIN WITH ANILIST",
                onClick = {
                    val url = "https://anilist.co/api/v2/oauth/authorize?client_id=${AnilistApi.CLIENT_ID}&response_type=token"
                    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Social links row
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
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

// Helper to use dp value as sp for letter spacing
private inline val Float.sp get() =
    androidx.compose.ui.unit
        .TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)

@Preview(showBackground = true, showSystemUi = true)
@Composable
@Suppress("UnusedPrivateMember") // Compose @Preview targets — surfaced by Android Studio's preview tooling.
private fun LoginScreenPreview() {
    LoginScreen()
}
