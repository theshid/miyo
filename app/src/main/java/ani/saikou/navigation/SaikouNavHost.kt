package ani.saikou.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ani.saikou.screens.anime.AnimeScreen
import ani.saikou.screens.character.CharacterDetailScreen
import ani.saikou.screens.detail.MediaDetailScreen
import ani.saikou.screens.error.NoInternetScreen
import ani.saikou.screens.home.HomeScreen
import ani.saikou.screens.lists.UserListsScreen
import ani.saikou.screens.login.LoginScreen
import ani.saikou.screens.manga.MangaScreen
import ani.saikou.screens.player.VideoPlayerScreen
import ani.saikou.screens.reader.MangaReaderScreen
import ani.saikou.screens.search.SearchScreen
import ani.saikou.screens.news.NewsFeedScreen
import ani.saikou.screens.torrent.TorrentSearchScreen

@Composable
fun SaikouNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Login.route,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
        popExitTransition = { fadeOut(animationSpec = tween(300)) },
    ) {
        // ── Login ─────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Bottom Nav Screens ────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAnimeList = { navController.navigate(Screen.UserLists.createRoute("ANIME")) },
                onNavigateToMangaList = { navController.navigate(Screen.UserLists.createRoute("MANGA")) },
                onNavigateToMedia = { id -> navController.navigate(Screen.MediaDetail.createRoute(id)) },
                onNavigateToNews = { navController.navigate(Screen.News.route) },
            )
        }

        composable(Screen.Anime.route) {
            AnimeScreen(
                onNavigateToMedia = { id -> navController.navigate(Screen.MediaDetail.createRoute(id)) },
                onNavigateToSearch = { genre -> navController.navigate(Screen.Search.createRoute(genre = genre, type = "ANIME")) },
            )
        }

        composable(Screen.Manga.route) {
            MangaScreen(
                onNavigateToMedia = { id -> navController.navigate(Screen.MediaDetail.createRoute(id)) },
                onNavigateToSearch = { genre -> navController.navigate(Screen.Search.createRoute(genre = genre, type = "MANGA")) },
            )
        }

        // ── Media Detail ──────────────────────────────────────
        composable(
            route = Screen.MediaDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getInt("id") ?: return@composable
            MediaDetailScreen(
                mediaId = mediaId,
                onBack = { navController.popBackStack() },
                onNavigateToCharacter = { id -> navController.navigate(Screen.CharacterDetail.createRoute(id)) },
                onNavigateToPlayer = { episodeNum -> navController.navigate(Screen.VideoPlayer.createRoute(mediaId, episodeNum)) },
                onNavigateToReader = { chapterNum -> navController.navigate(Screen.MangaReader.createRoute(mediaId, chapterNum)) },
                onNavigateToMedia = { id -> navController.navigate(Screen.MediaDetail.createRoute(id)) },
                onNavigateToTorrent = { query -> navController.navigate(Screen.TorrentSearch.createRoute(query)) },
            )
        }

        // ── Character Detail ──────────────────────────────────
        composable(
            route = Screen.CharacterDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getInt("id") ?: return@composable
            CharacterDetailScreen(
                characterId = characterId,
                onBack = { navController.popBackStack() },
                onNavigateToMedia = { id -> navController.navigate(Screen.MediaDetail.createRoute(id)) },
            )
        }

        // ── Search ────────────────────────────────────────────
        composable(
            route = Screen.Search.route,
            arguments = listOf(
                navArgument("genre") { type = NavType.StringType; defaultValue = "" },
                navArgument("type") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val initialGenre = backStackEntry.arguments?.getString("genre")?.takeIf { it.isNotEmpty() }
            val initialType = backStackEntry.arguments?.getString("type")?.takeIf { it.isNotEmpty() }
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToMedia = { id -> navController.navigate(Screen.MediaDetail.createRoute(id)) },
                initialGenre = initialGenre,
                initialType = initialType,
            )
        }

        // ── User Lists ────────────────────────────────────────
        composable(
            route = Screen.UserLists.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "ANIME"
            UserListsScreen(
                type = type,
                onBack = { navController.popBackStack() },
                onNavigateToMedia = { id -> navController.navigate(Screen.MediaDetail.createRoute(id)) },
            )
        }

        // ── Video Player ──────────────────────────────────────
        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.IntType },
                navArgument("episodeNum") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: return@composable
            val episodeNum = backStackEntry.arguments?.getInt("episodeNum") ?: return@composable
            VideoPlayerScreen(
                mediaId = mediaId,
                episodeNum = episodeNum,
                onBack = { navController.popBackStack() },
            )
        }

        // ── Manga Reader ─────────────────────────────────────
        composable(
            route = Screen.MangaReader.route,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.IntType },
                navArgument("chapterNum") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: return@composable
            val chapterNum = backStackEntry.arguments?.getInt("chapterNum") ?: return@composable
            MangaReaderScreen(
                mediaId = mediaId,
                chapterNum = chapterNum,
                onBack = { navController.popBackStack() },
            )
        }

        // ── News Feed ─────────────────────────────────────────
        composable(Screen.News.route) {
            NewsFeedScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ── Torrent Search ────────────────────────────────────
        composable(
            route = Screen.TorrentSearch.route,
            arguments = listOf(
                navArgument("query") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            TorrentSearchScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ── No Internet ──────────────────────────────────────
        composable(Screen.NoInternet.route) {
            NoInternetScreen(
                onRetry = { navController.popBackStack() },
            )
        }
    }
}
