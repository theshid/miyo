package ani.saikou

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import ani.saikou.data.local.TokenStorage
import org.koin.android.ext.android.inject

/**
 * Receives the AniList OAuth callback (`miyo://anilist#access_token=...`),
 * extracts and saves the token, then relaunches MainActivity.
 *
 * This is a separate activity because Custom Tabs redirect needs to close
 * the browser tab and bring the app back to the foreground.
 */
class LoginCallbackActivity : ComponentActivity() {

    private val tokenStorage: TokenStorage by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data
        if (data != null) {
            // AniList returns: miyo://anilist#access_token=TOKEN&token_type=Bearer&expires_in=...
            // The fragment (#...) is in data.toString(), not data.fragment on all devices
            val fullUri = data.toString()
            val token = Regex("""(?<=access_token=).+(?=&token_type)""").find(fullUri)?.value

            if (token != null) {
                tokenStorage.saveToken(token)
            }
        }

        // Relaunch MainActivity (clears the Custom Tab from the back stack)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
