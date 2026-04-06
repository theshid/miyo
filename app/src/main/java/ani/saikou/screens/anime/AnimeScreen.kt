package ani.saikou.screens.anime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ani.saikou.ui.theme.OnSurface

@Composable
fun AnimeScreen(
    onNavigateToMedia: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        // TODO: Search bar + avatar
        // TODO: Trending Now carousel
        // TODO: Genres / Top Rated chips
        // TODO: Recently Updated horizontal scroll
        // TODO: Popular This Season vertical list
        Text(
            text = "Anime Discovery",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )
    }
}
