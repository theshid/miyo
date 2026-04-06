package ani.saikou.data.remote.torrent

import ani.saikou.domain.model.TorrentQuality
import ani.saikou.domain.model.TorrentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class NyaaSource : TorrentSource {

    override val name = "NYAA"

    override suspend fun search(query: String): List<TorrentResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nyaa.si/?f=0&c=1_2&q=$encoded&s=seeders&o=desc"

            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            doc.select("table.torrent-list tbody tr").mapNotNull { row ->
                try {
                    val cols = row.select("td")
                    if (cols.size < 7) return@mapNotNull null

                    val titleEl = cols[1].select("a:not(.comments)").last() ?: return@mapNotNull null
                    val title = titleEl.text()

                    val links = cols[2].select("a")
                    val magnet = links.firstOrNull { it.attr("href").startsWith("magnet:") }
                        ?.attr("href") ?: return@mapNotNull null
                    val torrentFile = links.firstOrNull { it.attr("href").endsWith(".torrent") }
                        ?.let { "https://nyaa.si${it.attr("href")}" }

                    val size = cols[3].text()
                    val date = cols[4].text()
                    val seeders = cols[5].text().toIntOrNull() ?: 0
                    val leechers = cols[6].text().toIntOrNull() ?: 0

                    TorrentResult(
                        title = title,
                        magnetLink = magnet,
                        size = size,
                        seeders = seeders,
                        leechers = leechers,
                        date = date,
                        source = name,
                        torrentFileUrl = torrentFile,
                        quality = TorrentQuality.parse(title),
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
