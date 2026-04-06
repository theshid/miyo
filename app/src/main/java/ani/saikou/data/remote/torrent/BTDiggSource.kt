package ani.saikou.data.remote.torrent

import ani.saikou.domain.model.TorrentQuality
import ani.saikou.domain.model.TorrentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

class BTDiggSource : TorrentSource {

    override val name = "BTDIGG"

    override suspend fun search(query: String): List<TorrentResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://btdig.com/search?q=$encoded&order=0"

            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            doc.select("div.one_result").mapNotNull { item ->
                try {
                    val titleEl = item.select("div.torrent_name a").first() ?: return@mapNotNull null
                    val title = titleEl.text()

                    val magnet = item.select("a[href^=magnet:]").attr("href")
                    if (magnet.isBlank()) return@mapNotNull null

                    val infoSpans = item.select("div.torrent_size, span.torrent_size")
                    val size = infoSpans.firstOrNull()?.text() ?: "?"

                    val filesText = item.select("span.torrent_files, div.torrent_files").text()

                    TorrentResult(
                        title = title,
                        magnetLink = magnet,
                        size = size,
                        seeders = 0,
                        leechers = 0,
                        date = "",
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
