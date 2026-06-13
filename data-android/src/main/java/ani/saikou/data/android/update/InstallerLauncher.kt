package ani.saikou.data.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ani.saikou.domain.model.update.UpdateError
import java.io.File

/**
 * Fires ACTION_VIEW on the prepared APK with a content:// URI from the
 * already-declared FileProvider. We never claim or attempt silent install —
 * Android's package installer takes over from here and the user confirms.
 *
 * FLAG_GRANT_READ_URI_PERMISSION is mandatory; without it the installer
 * activity cannot read the URI we hand it. FLAG_ACTIVITY_NEW_TASK lets the
 * intent fire from any (non-activity) context — important because we may
 * be triggered from a ViewModel-side action that doesn't hold an Activity.
 */
internal class InstallerLauncher(
    private val context: Context,
    private val fileProviderAuthority: String,
) {
    fun launch(apk: File) {
        if (!apk.exists()) {
            throw UpdateError.InstallerLaunchFailed(IllegalStateException("APK not found: ${apk.name}"))
        }
        val uri: Uri =
            try {
                FileProvider.getUriForFile(context, fileProviderAuthority, apk)
            } catch (e: Exception) {
                throw UpdateError.InstallerLaunchFailed(e)
            }
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            throw UpdateError.InstallerLaunchFailed(e)
        }
    }
}
