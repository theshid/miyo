package ani.saikou.domain.usecase.update

import ani.saikou.domain.model.update.InstalledAppInfo
import ani.saikou.domain.model.update.UpdateAvailability
import ani.saikou.domain.model.update.UpdateManifest

/**
 * Pure decision: given what's installed and what the manifest advertises,
 * is there an update, and is the user allowed to skip it?
 *
 * - Compares INTEGER [versionCode] only — version-name strings are display copy.
 * - Mandatory when [`UpdateManifest.mandatory`] is true OR the installed
 *   versionCode is strictly below [`UpdateManifest.minimumSupportedVersion`].
 */
object UpdateDecisionEngine {
    fun evaluate(
        installed: InstalledAppInfo,
        manifest: UpdateManifest,
    ): UpdateAvailability {
        if (manifest.versionCode <= installed.versionCode) {
            return UpdateAvailability.UpToDate
        }
        val mandatory =
            manifest.mandatory ||
                installed.versionCode < manifest.minimumSupportedVersion
        return UpdateAvailability.Available(manifest, mandatory)
    }
}
