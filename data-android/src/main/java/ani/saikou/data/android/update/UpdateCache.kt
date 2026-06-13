package ani.saikou.data.android.update

import android.content.Context
import ani.saikou.domain.model.update.UpdateManifest
import java.io.File

/**
 * Manages files under `cacheDir/updates/`. APKs are named by versionCode so
 * cleanup can identify and preserve the artifact we're about to install while
 * removing every other generation.
 *
 * The directory mirrors the path declared in app/src/main/res/xml/file_paths.xml —
 * any change here MUST be reflected in the FileProvider configuration or the
 * install intent will fail with SecurityException.
 */
internal class UpdateCache(
    private val context: Context,
) {
    fun directory(): File {
        val dir = File(context.cacheDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun fileFor(manifest: UpdateManifest): File = File(directory(), fileName(manifest.versionCode))

    fun tempFileFor(manifest: UpdateManifest): File = File(directory(), "${fileName(manifest.versionCode)}.part")

    fun cleanup(current: UpdateManifest?) {
        // Preserve BOTH the final artifact name and the in-flight .part for
        // the current versionCode. Without the latter, the post-check
        // cleanup race could delete an actively writing temp file the
        // moment the user taps Update.
        val keepNames =
            current
                ?.let {
                    val final = fileName(it.versionCode)
                    setOf(final, "$final.part")
                }.orEmpty()
        directory().listFiles()?.forEach { file ->
            if (file.name in keepNames) return@forEach
            file.delete()
        }
    }

    private fun fileName(versionCode: Int): String = "miyo-vc-$versionCode.apk"

    companion object {
        const val DIR_NAME = "updates"
    }
}
