package ani.saikou.presentation.screens.update

import ani.saikou.domain.model.update.InstalledAppInfo
import ani.saikou.domain.model.update.UpdateArtifactRef
import ani.saikou.domain.model.update.UpdateError
import ani.saikou.domain.model.update.UpdateManifest
import ani.saikou.domain.model.update.UpdateProgress
import ani.saikou.domain.repository.UpdateRepository
import ani.saikou.domain.usecase.update.CheckForUpdateUseCase
import ani.saikou.domain.usecase.update.CleanupUpdateArtifactsUseCase
import ani.saikou.domain.usecase.update.DownloadUpdateUseCase
import ani.saikou.domain.usecase.update.InstallUpdateUseCase
import ani.saikou.platform.log.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private val repository: UpdateRepository = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxUnitFun = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is hidden`() =
        testScope.runTest {
            val vm = newVm()
            assertEquals(UpdateUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun `checkOnce transitions to Available when remote is newer`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)

            val vm = newVm()
            vm.checkOnce()
            runCurrent()

            val state = vm.uiState.value
            assertTrue(state is UpdateUiState.Available)
            assertEquals(false, (state as UpdateUiState.Available).mandatory)
        }

    @Test
    fun `checkOnce flags mandatory when installed below minimumSupportedVersion`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns
                manifest(
                    versionCode = 5,
                    mandatory = false,
                    minSupported = 4,
                )
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 3)

            val vm = newVm()
            vm.checkOnce()
            runCurrent()

            val state = vm.uiState.value as UpdateUiState.Available
            assertEquals(true, state.mandatory)
        }

    @Test
    fun `checkOnce stays hidden when up to date`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 4)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)

            val vm = newVm()
            vm.checkOnce()
            runCurrent()

            assertEquals(UpdateUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun `checkOnce stays hidden on UpdateError — never blocks the app`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } throws UpdateError.ManifestUnreachable(503)

            val vm = newVm()
            vm.checkOnce()
            runCurrent()

            assertEquals(UpdateUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun `checkOnce is idempotent within a process`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)

            val vm = newVm()
            vm.checkOnce()
            vm.checkOnce()
            vm.checkOnce()
            runCurrent()

            coVerify(exactly = 1) { repository.fetchManifest() }
        }

    @Test
    fun `dismiss hides an optional update`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)
            val vm = newVm()
            vm.checkOnce()
            runCurrent()

            vm.dismiss()

            assertEquals(UpdateUiState.Hidden, vm.uiState.value)
        }

    @Test
    fun `dismiss is a no-op on a mandatory update`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5, mandatory = true)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)
            val vm = newVm()
            vm.checkOnce()
            runCurrent()

            val before = vm.uiState.value
            vm.dismiss()

            assertEquals(before, vm.uiState.value)
            assertTrue(vm.uiState.value is UpdateUiState.Available)
        }

    @Test
    fun `startDownload walks Downloading-Verifying-ReadyToInstall on a successful flow`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)
            every { repository.downloadAndPrepare(any()) } returns
                flowOf(
                    UpdateProgress.Idle,
                    UpdateProgress.Downloading(bytesRead = 100, totalBytes = 1_000),
                    UpdateProgress.Downloading(bytesRead = 1_000, totalBytes = 1_000),
                    UpdateProgress.Verifying,
                    UpdateProgress.Ready(UpdateArtifactRef("/tmp/miyo.apk")),
                )

            val vm = newVm()
            vm.checkOnce()
            runCurrent()
            vm.startDownload()
            runCurrent()

            val state = vm.uiState.value
            assertTrue(state is UpdateUiState.ReadyToInstall)
            assertEquals("/tmp/miyo.apk", (state as UpdateUiState.ReadyToInstall).artifact.token)
        }

    @Test
    fun `failed download sets Failed state with the underlying UpdateError`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)
            every { repository.downloadAndPrepare(any()) } returns
                flowOf(
                    UpdateProgress.Downloading(bytesRead = 100, totalBytes = 1_000),
                    UpdateProgress.Failed(UpdateError.ChecksumMismatch),
                )

            val vm = newVm()
            vm.checkOnce()
            runCurrent()
            vm.startDownload()
            runCurrent()

            val state = vm.uiState.value as UpdateUiState.Failed
            assertEquals(UpdateError.ChecksumMismatch, state.error)
        }

    @Test
    fun `install routes through AwaitingInstallPermission when permission is missing`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)
            every { repository.downloadAndPrepare(any()) } returns
                flowOf(
                    UpdateProgress.Ready(UpdateArtifactRef("/tmp/miyo.apk")),
                )
            every { repository.isInstallPermissionGranted() } returns false

            val vm = newVm()
            vm.checkOnce()
            runCurrent()
            vm.startDownload()
            runCurrent()
            vm.install()

            val state = vm.uiState.value
            assertTrue(state is UpdateUiState.AwaitingInstallPermission)
        }

    @Test
    fun `install routes straight to installer when permission is granted`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)
            every { repository.downloadAndPrepare(any()) } returns
                flowOf(
                    UpdateProgress.Ready(UpdateArtifactRef("/tmp/miyo.apk")),
                )
            every { repository.isInstallPermissionGranted() } returns true

            val vm = newVm()
            vm.checkOnce()
            runCurrent()
            vm.startDownload()
            runCurrent()
            vm.install()
            runCurrent()

            coVerify { repository.startInstall(UpdateArtifactRef("/tmp/miyo.apk")) }
        }

    @Test
    fun `onForegrounded resumes install when permission was just granted`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)
            every { repository.downloadAndPrepare(any()) } returns
                flowOf(
                    UpdateProgress.Ready(UpdateArtifactRef("/tmp/miyo.apk")),
                )
            every { repository.isInstallPermissionGranted() } returnsMany listOf(false, true)

            val vm = newVm()
            vm.checkOnce()
            runCurrent()
            vm.startDownload()
            runCurrent()
            vm.install() // first install attempt → AwaitingInstallPermission
            runCurrent()
            vm.onForegrounded()
            runCurrent()

            coVerify { repository.startInstall(UpdateArtifactRef("/tmp/miyo.apk")) }
            // State should be back to ReadyToInstall so a subsequent resume
            // (after the user cancels Android's installer) doesn't re-fire.
            assertTrue(vm.uiState.value is UpdateUiState.ReadyToInstall)
        }

    @Test
    fun `retry after installer failure forces re-download — defensive against evicted cache`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)
            every { repository.downloadAndPrepare(any()) } returns
                flowOf(
                    UpdateProgress.Ready(UpdateArtifactRef("/tmp/miyo.apk")),
                )
            every { repository.isInstallPermissionGranted() } returns true
            coEvery {
                repository.startInstall(UpdateArtifactRef("/tmp/miyo.apk"))
            } throws UpdateError.InstallerLaunchFailed()

            val vm = newVm()
            vm.checkOnce()
            runCurrent()
            vm.startDownload()
            runCurrent()
            vm.install()
            runCurrent()
            // First install attempt blew up. Failed state drops the (possibly
            // evicted) artifact so Retry takes the re-download path.
            val failed = vm.uiState.value as UpdateUiState.Failed
            assertEquals(null, failed.artifact)

            vm.retry()
            runCurrent()

            // Retry triggered a fresh download → ReadyToInstall again. The
            // user (or automation) taps Install on the new artifact.
            assertTrue(vm.uiState.value is UpdateUiState.ReadyToInstall)
            io.mockk.verify(exactly = 2) { repository.downloadAndPrepare(any()) }
        }

    @Test
    fun `onForegrounded does not re-launch installer if state is not awaiting permission`() =
        testScope.runTest {
            coEvery { repository.fetchManifest() } returns manifest(versionCode = 5)
            coEvery { repository.installedAppInfo() } returns installed(versionCode = 4)
            every { repository.downloadAndPrepare(any()) } returns
                flowOf(
                    UpdateProgress.Ready(UpdateArtifactRef("/tmp/miyo.apk")),
                )
            every { repository.isInstallPermissionGranted() } returnsMany listOf(false, true, true)

            val vm = newVm()
            vm.checkOnce()
            runCurrent()
            vm.startDownload()
            runCurrent()
            vm.install() // → AwaitingInstallPermission
            runCurrent()
            vm.onForegrounded() // → ReadyToInstall + first startInstall
            runCurrent()
            vm.onForegrounded() // user cancelled installer; must NOT re-fire
            runCurrent()

            coVerify(exactly = 1) { repository.startInstall(UpdateArtifactRef("/tmp/miyo.apk")) }
        }

    private fun newVm(): UpdateViewModel =
        UpdateViewModel(
            checkForUpdate = CheckForUpdateUseCase(repository),
            downloadUpdate = DownloadUpdateUseCase(repository),
            installUpdate = InstallUpdateUseCase(repository),
            cleanupArtifacts = CleanupUpdateArtifactsUseCase(repository),
            repository = repository,
            logger = logger,
        )

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
            apkUrl = "https://example.invalid/m.apk",
            sha256 = "0".repeat(64),
            releaseNotes = "Notes",
            mandatory = mandatory,
        )
}
