package ani.saikou.domain.usecase.torrents

import ani.saikou.domain.model.TorrentResult
import ani.saikou.domain.repository.TorrentRepository

/**
 * "Find torrents matching this query" — fans out across configured
 * trackers via the repository. Empty/blank queries return an empty
 * list (the VM handles the input-side debounce; the use case is a
 * pure data verb).
 */
class SearchTorrentsUseCase(
    private val repository: TorrentRepository,
) {
    suspend operator fun invoke(query: String): List<TorrentResult> = repository.search(query)
}
