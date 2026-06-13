package ani.saikou.domain.usecase.update

import ani.saikou.domain.model.update.UpdateArtifactRef
import ani.saikou.domain.repository.UpdateRepository

/**
 * Hand the prepared APK to the system package installer. Throws
 * [`ani.saikou.domain.model.update.UpdateError.InstallerLaunchFailed`] if the
 * intent could not be resolved.
 */
class InstallUpdateUseCase(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(artifact: UpdateArtifactRef) {
        repository.startInstall(artifact)
    }
}
