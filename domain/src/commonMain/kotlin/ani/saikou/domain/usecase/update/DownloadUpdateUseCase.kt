package ani.saikou.domain.usecase.update

import ani.saikou.domain.model.update.UpdateManifest
import ani.saikou.domain.model.update.UpdateProgress
import ani.saikou.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.Flow

/**
 * Stream APK download + verification progress for the supplied manifest.
 * The use case is intentionally thin — orchestration belongs in the VM,
 * which decides when to retry, when to surface a banner, etc.
 */
class DownloadUpdateUseCase(
    private val repository: UpdateRepository,
) {
    operator fun invoke(manifest: UpdateManifest): Flow<UpdateProgress> = repository.downloadAndPrepare(manifest)
}
