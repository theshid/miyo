package ani.saikou.data.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Wraps the canRequestPackageInstalls() check and the deep-link to the
 * per-app "Install unknown apps" toggle. minSdk for the project is 26 so
 * both branches (Build.VERSION_CODES.O+) always apply.
 */
internal class UnknownSourcesGuard(
    private val context: Context,
) {
    fun isPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openSettings() {
        val intent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: generic security settings if the package-scoped action
            // is unavailable (rare; some OEM-modded ROMs).
            val fallback =
                Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(fallback)
        }
    }
}
