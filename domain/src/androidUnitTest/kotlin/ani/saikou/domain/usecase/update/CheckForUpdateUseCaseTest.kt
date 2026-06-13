package ani.saikou.domain.usecase.update

import ani.saikou.domain.model.update.InstalledAppInfo
import ani.saikou.domain.model.update.UpdateAvailability
import ani.saikou.domain.model.update.UpdateError
import ani.saikou.domain.model.update.UpdateManifest
import ani.saikou.domain.repository.UpdateRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CheckForUpdateUseCaseTest {
    private val repository: UpdateRepository = mockk()
    private val useCase = CheckForUpdateUseCase(repository)

    @Test
    fun `up to date when remote versionCode equals installed`() =
        runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 4)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)

            val result = useCase()

            assertEquals(UpdateAvailability.UpToDate, result)
        }

    @Test
    fun `available and optional when remote is newer and not mandatory`() =
        runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5, mandatory = false, minSupported = 1)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)

            val result = useCase()

            assertTrue(result is UpdateAvailability.Available)
            assertEquals(false, (result as UpdateAvailability.Available).mandatory)
        }

    @Test
    fun `propagates manifest unreachable errors`() =
        runTest {
            coEvery { repository.fetchManifest() } throws UpdateError.ManifestUnreachable(statusCode = 503)

            try {
                useCase()
                fail("expected ManifestUnreachable to propagate")
            } catch (e: UpdateError.ManifestUnreachable) {
                assertEquals(503, e.statusCode)
            }
        }

    @Test
    fun `propagates malformed manifest errors`() =
        runTest {
            coEvery { repository.fetchManifest() } throws UpdateError.ManifestMalformed()

            try {
                useCase()
                fail("expected ManifestMalformed to propagate")
            } catch (_: UpdateError.ManifestMalformed) {
                // expected
            }
        }

    private fun installed(versionCode: Int): InstalledAppInfo =
        InstalledAppInfo(
            packageName = "ani.saikou.v2",
            versionCode = versionCode,
            versionName = "1.2.2",
            signerSha256 = "0".repeat(64),
        )

    private fun manifest(
        versionCode: Int,
        mandatory: Boolean = false,
        minSupported: Int = 1,
    ): UpdateManifest =
        UpdateManifest(
            versionCode = versionCode,
            versionName = "1.3.0",
            minimumSupportedVersion = minSupported,
            apkUrl = "https://example.invalid/miyo.apk",
            sha256 = "0".repeat(64),
            releaseNotes = "Release",
            mandatory = mandatory,
        )
}
