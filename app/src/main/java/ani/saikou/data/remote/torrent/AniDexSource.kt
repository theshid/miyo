package ani.saikou.data.remote.torrent

import ani.saikou.domain.model.TorrentQuality
import ani.saikou.domain.model.TorrentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class AniDexSource : TorrentSource {

    override val name = "ANIDEX"

    override suspend fun search(query: String): List<TorrentResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://anidex.info/?q=$encoded&id=1"

            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            doc.select("div.table-responsive tbody tr").mapNotNull { row ->
                try {
                    val cols = row.select("td")
                    if (cols.size < 8) return@mapNotNull null

                    val titleEl = cols[2].select("a").firstOrNull() ?: return@mapNotNull null
                    val title = titleEl.attr("title").ifEmpty { titleEl.text() }

                    val magnet = cols[4].select("a[href^=magnet:]").attr("href")
                    if (magnet.isBlank()) return@mapNotNull null

                    val size = cols[6].text()
                    val seeders = cols[7].text().toIntOrNull() ?: 0
                    val leechers = cols[8].text().toIntOrNull() ?: 0
                    val date = cols[9].text()

                    TorrentResult(
                        title = title,
                        magnetLink = magnet,
                        size = size,
                        seeders = seeders,
                        leechers = leechers,
                        date = date,
                        source = name,
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
