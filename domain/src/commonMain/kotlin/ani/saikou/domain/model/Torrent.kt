package ani.saikou.domain.model

data class TorrentResult(
    val title: String,
    val magnetLink: String,
    val size: String,
    val seeders: Int,
    val leechers: Int,
    val date: String,
    val source: String,
    val torrentFileUrl: String? = null,
    val quality: TorrentQuality? = null,
)

data class TorrentQuality(
    val resolution: String?, // 1080p, 720p, 480p
    val videoSource: String?, // BluRay, BD, Web, HDTV
    val codec: String?, // HEVC, x265, x264
    val isBatch: Boolean,
) {
    companion object {
        fun parse(title: String): TorrentQuality {
            val res =
                Regex("""(2160|1080|720|480|360)[pi]""", RegexOption.IGNORE_CASE)
                    .find(title)
                    ?.value
                    ?.lowercase()
            val src =
                when {
                    Regex("""Blu-?Ray|BD|BDMV|BDRip""", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "BluRay"
                    Regex("""Web-?DL|WEB-?Rip|WEB""", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "Web"
                    Regex("""HDTV""", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "HDTV"
                    else -> null
                }
            val codec =
                when {
                    Regex("""HEVC|[hH]\.?265|x265""").containsMatchIn(title) -> "HEVC"
                    Regex("""[hH]\.?264|x264""").containsMatchIn(title) -> "x264"
                    Regex("""AV1""", RegexOption.IGNORE_CASE).containsMatchIn(title) -> "AV1"
                    else -> null
                }
            val batch =
                Regex("""Batch|Complete|S\d+(?!\s*E)|Season\s*\d""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(title)

            return TorrentQuality(resolution = res, videoSource = src, codec = codec, isBatch = batch)
        }
    }
}
