package ani.saikou.data.repository

import ani.saikou.domain.model.TorrentResult
import ani.saikou.domain.repository.TorrentRepository
import ani.saikou.domain.source.TorrentSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Fan-out aggregator over the configured [TorrentSource]s. Same shape
 * as [NewsRepositoryImpl] but without a dedup pass — torrent rows from
 * different trackers are deliberately distinct (different magnet links,
 * different seeder counts). Per-source failures are swallowed so a
 * single dead tracker doesn't blank out the whole list.
 */
class TorrentRepositoryImpl(
    private val sources: List<TorrentSource>,
) : TorrentRepository {
    override suspend fun search(query: String): List<TorrentResult> =
        coroutineScope {
            sources
                .map { source ->
                    async {
                        try {
                            source.search(query)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll()
                .flatten()
        }
}
