package ani.saikou.domain.usecase.update

import ani.saikou.domain.model.update.UpdateManifest
import ani.saikou.domain.repository.UpdateRepository

/**
 * Removes stale APKs from the update cache. Called opportunistically after
 * a successful check — keeps cacheDir bounded so a flaky update flow doesn't
 * accumulate orphan APKs across versions.
 */
class CleanupUpdateArtifactsUseCase(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(current: UpdateManifest? = null) {
        repository.cleanupStaleArtifacts(current)
    }
}
