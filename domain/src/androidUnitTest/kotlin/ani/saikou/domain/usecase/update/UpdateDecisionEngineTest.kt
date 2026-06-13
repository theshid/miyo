package ani.saikou.domain.usecase.update

import ani.saikou.domain.model.update.InstalledAppInfo
import ani.saikou.domain.model.update.UpdateAvailability
import ani.saikou.domain.model.update.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDecisionEngineTest {
    private val installed4 = installedAt(versionCode = 4)
    private val installed1 = installedAt(versionCode = 1)

    @Test
    fun `same versionCode means up to date`() {
        val result =
            UpdateDecisionEngine.evaluate(
                installed = installed4,
                manifest = manifest(versionCode = 4),
            )
        assertEquals(UpdateAvailability.UpToDate, result)
    }

    @Test
    fun `lower remote versionCode means up to date — manifest never downgrades`() {
        val result =
            UpdateDecisionEngine.evaluate(
                installed = installed4,
                manifest = manifest(versionCode = 3),
            )
        assertEquals(UpdateAvailability.UpToDate, result)
    }

    @Test
    fun `higher versionCode with mandatory=false yields an optional update`() {
        val manifest = manifest(versionCode = 5, mandatory = false, minSupported = 1)
        val result = UpdateDecisionEngine.evaluate(installed4, manifest)
        assertTrue(result is UpdateAvailability.Available)
        val available = result as UpdateAvailability.Available
        assertEquals(manifest, available.manifest)
        assertEquals(false, available.mandatory)
    }

    @Test
    fun `higher versionCode with mandatory=true forces mandatory`() {
        val manifest = manifest(versionCode = 5, mandatory = true, minSupported = 1)
        val result = UpdateDecisionEngine.evaluate(installed4, manifest)
        assertTrue(result is UpdateAvailability.Available)
        assertEquals(true, (result as UpdateAvailability.Available).mandatory)
    }

    @Test
    fun `installed below minimumSupportedVersion forces mandatory`() {
        // installed=1, min=3 → user must update even if mandatory flag is false.
        val manifest = manifest(versionCode = 5, mandatory = false, minSupported = 3)
        val result = UpdateDecisionEngine.evaluate(installed1, manifest)
        assertTrue(result is UpdateAvailability.Available)
        assertEquals(true, (result as UpdateAvailability.Available).mandatory)
    }

    @Test
    fun `installed at minimumSupportedVersion is still optional when flag is false`() {
        // minSupported is INCLUSIVE — only versions strictly below trigger mandatory.
        val manifest = manifest(versionCode = 5, mandatory = false, minSupported = 4)
        val result = UpdateDecisionEngine.evaluate(installed4, manifest)
        assertTrue(result is UpdateAvailability.Available)
        assertEquals(false, (result as UpdateAvailability.Available).mandatory)
    }

    @Test
    fun `versionName strings are not used for ordering`() {
        // remote versionName lexically lower than installed should still trigger update
        // because the integer versionCode is higher.
        val installed = installedAt(versionCode = 10, versionName = "1.9.0")
        val manifest = manifest(versionCode = 11, versionName = "1.10.0")
        val result = UpdateDecisionEngine.evaluate(installed, manifest)
        assertTrue(result is UpdateAvailability.Available)
    }

    private fun installedAt(
        versionCode: Int,
        versionName: String = "1.0.0",
    ): InstalledAppInfo =
        InstalledAppInfo(
            packageName = "ani.saikou.v2",
            versionCode = versionCode,
            versionName = versionName,
            signerSha256 = "0".repeat(64),
        )

    private fun manifest(
        versionCode: Int,
        versionName: String = "x.y.z",
        mandatory: Boolean = false,
        minSupported: Int = 1,
    ): UpdateManifest =
        UpdateManifest(
            versionCode = versionCode,
            versionName = versionName,
            minimumSupportedVersion = minSupported,
            apkUrl = "https://example.invalid/miyo.apk",
            sha256 = "0".repeat(64),
            releaseNotes = "",
            mandatory = mandatory,
            publishedAt = null,
        )
}
