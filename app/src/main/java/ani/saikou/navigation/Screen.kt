package ani.saikou.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String = "",
    val icon: ImageVector? = null,
) {
    // ── Bottom Nav Screens ────────────────────────────────────
    data object Anime : Screen("anime", "Anime", Icons.Default.PlayArrow)
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Manga : Screen("manga", "Manga", Icons.Default.Book)

    // ── News ──────────────────────────────────────────────────
    data object News : Screen("news")

    // ── Splash ────────────────────────────────────────────────
    data object Splash : Screen("splash")

    // ── Auth ──────────────────────────────────────────────────
    data object Login : Screen("login")

    // ── Detail Screens ────────────────────────────────────────
    data object MediaDetail : Screen("media/{id}") {
        fun createRoute(id: Int) = "media/$id"
    }

    data object CharacterDetail : Screen("character/{id}") {
        fun createRoute(id: Int) = "character/$id"
    }

    // ── Search & Lists ────────────────────────────────────────
    data object Search : Screen("search?genre={genre}&type={type}") {
        fun createRoute(genre: String? = null, type: String? = null): String {
            val base = "search"
            val params = mutableListOf<String>()
            if (genre != null) params.add("genre=$genre")
            if (type != null) params.add("type=$type")
            return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        }
    }

    data object UserLists : Screen("lists/{type}") {
        fun createRoute(type: String) = "lists/$type"
    }

    // ── Players / Readers ─────────────────────────────────────
    data object VideoPlayer : Screen("player/{mediaId}/{episodeNum}?sourceSlug={sourceSlug}") {
        fun createRoute(mediaId: Int, episodeNum: Int, sourceSlug: String? = null): String {
            val base = "player/$mediaId/$episodeNum"
            return if (sourceSlug != null) "$base?sourceSlug=$sourceSlug" else base
        }
    }

    data object MangaReader : Screen("reader/{mediaId}/{chapterNum}?sourceId={sourceId}") {
        fun createRoute(mediaId: Int, chapterNum: Int, sourceId: String? = null): String {
            val base = "reader/$mediaId/$chapterNum"
            return if (sourceId != null) "$base?sourceId=$sourceId" else base
        }
    }

    // ── Downloads ─────────────────────────────────────────────
    data object Downloads : Screen("downloads")

    // ── Torrent Search ──────────────────────────────────────────
    data object TorrentSearch : Screen("torrent?query={query}") {
        fun createRoute(query: String? = null): String =
            if (query != null) "torrent?query=$query" else "torrent"
    }

    // ── Seasonal Calendar ─────────────────────────────────────
    data object SeasonalCalendar : Screen("seasonal")

    // ── Stats Dashboard ──────────────────────────────────────
    data object Stats : Screen("stats")

    // ── AI Chat ──────────────────────────────────────────────
    data object AiChat : Screen("ai_chat")

    // ── Error ─────────────────────────────────────────────────
    data object NoInternet : Screen("no_internet")
}

val bottomBarScreens = listOf(Screen.Anime, Screen.Home, Screen.Manga)
