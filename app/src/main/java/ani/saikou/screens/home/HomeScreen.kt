package ani.saikou.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.Background
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant

@Composable
fun HomeScreen(
    onNavigateToAnimeList: () -> Unit,
    onNavigateToMangaList: () -> Unit,
    onNavigateToMedia: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        // TODO: User header with avatar, greeting, stats
        Text(
            text = "Hey, User",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // TODO: Stats cards (Episodes Watched / Chapters Read)

        // TODO: Anime List / Manga List action cards

        // TODO: Continue Watching horizontal scroll

        // TODO: Continue Reading horizontal scroll

        // TODO: Recommended For You horizontal scroll
    }
}
