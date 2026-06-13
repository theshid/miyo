package ani.saikou.domain.usecase.update

import ani.saikou.domain.model.update.UpdateAvailability
import ani.saikou.domain.repository.UpdateRepository

/**
 * Foreground update check: fetch the manifest, read the installed app info,
 * run the decision engine. Caller wraps the call site in a try/catch — every
 * failure mode is an [`ani.saikou.domain.model.update.UpdateError`] subtype,
 * and `CancellationException` is allowed to propagate.
 */
class CheckForUpdateUseCase(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(): UpdateAvailability {
        val manifest = repository.fetchManifest()
        val installed = repository.installedAppInfo()
        return UpdateDecisionEngine.evaluate(installed, manifest)
    }
}
