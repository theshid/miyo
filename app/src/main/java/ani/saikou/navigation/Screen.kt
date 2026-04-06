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
    data object Search : Screen("search")

    data object UserLists : Screen("lists/{type}") {
        fun createRoute(type: String) = "lists/$type"
    }

    // ── Players / Readers ─────────────────────────────────────
    data object VideoPlayer : Screen("player/{mediaId}/{episodeNum}") {
        fun createRoute(mediaId: Int, episodeNum: Int) = "player/$mediaId/$episodeNum"
    }

    data object MangaReader : Screen("reader/{mediaId}/{chapterNum}") {
        fun createRoute(mediaId: Int, chapterNum: Int) = "reader/$mediaId/$chapterNum"
    }

    // ── Error ─────────────────────────────────────────────────
    data object NoInternet : Screen("no_internet")
}

val bottomBarScreens = listOf(Screen.Anime, Screen.Home, Screen.Manga)
