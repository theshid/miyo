package ani.saikou.domain.source

import ani.saikou.domain.model.TorrentResult

interface TorrentSource {
    val name: String

    suspend fun search(query: String): List<TorrentResult>
}
