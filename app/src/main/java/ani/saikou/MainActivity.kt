package ani.saikou

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ani.saikou.di.AppModule
import ani.saikou.ui.theme.SaikouTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Handle OAuth callback from initial launch
        handleAnilistCallback(intent)

        setContent {
            SaikouTheme {
                SaikouApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAnilistCallback(intent)
    }

    private fun handleAnilistCallback(intent: Intent?) {
        // AniList OAuth returns: saikou://callback#access_token=...&token_type=Bearer&expires_in=...
        val data = intent?.data ?: return
        if (data.scheme != "saikou" || data.host != "callback") return

        // Fragment contains the token (after #)
        val fragment = data.fragment ?: return
        val token = fragment.split("&")
            .firstOrNull { it.startsWith("access_token=") }
            ?.substringAfter("access_token=")

        if (token != null) {
            AppModule.tokenStorage().saveToken(token)
        }
    }
}
