package ani.saikou.screens.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ani.saikou.ui.theme.OnSurface

@Composable
fun MediaDetailScreen(
    mediaId: Int,
    onBack: () -> Unit,
    onNavigateToCharacter: (Int) -> Unit,
    onNavigateToPlayer: (Int) -> Unit,
    onNavigateToReader: (Int) -> Unit,
    onNavigateToMedia: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // TODO: Collapsing banner header
        // TODO: Poster + title + status
        // TODO: Action row (Add to List, Favorite, Share)
        // TODO: Airing countdown card
        // TODO: Tab bar (Info, Episodes/Chapters, Characters, Related)
        Text(
            text = "Media Detail: $mediaId",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )
    }
}
