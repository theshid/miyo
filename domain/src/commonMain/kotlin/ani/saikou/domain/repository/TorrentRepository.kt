package ani.saikou.domain.repository

import ani.saikou.domain.model.TorrentResult

/**
 * Aggregator over the configured [ani.saikou.domain.source.TorrentSource]
 * implementations. Owns the multi-source fan-out the VM used to do
 * inline — Nyaa + BTDigg + AniDex queries run in parallel and their
 * results are concatenated. Per-source failures are swallowed so a
 * single dead provider doesn't blank out the whole list.
 *
 * No dedup — torrent rows from different trackers are intentionally
 * separate (different magnets, different seeder counts), and the
 * screen's source-filter chip relies on per-source attribution.
 */
interface TorrentRepository {
    suspend fun search(query: String): List<TorrentResult>
}
