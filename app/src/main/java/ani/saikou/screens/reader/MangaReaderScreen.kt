package ani.saikou.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ani.saikou.ui.theme.OnSurface

@Composable
fun MangaReaderScreen(
    mediaId: Int,
    chapterNum: Int,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // TODO: Vertical/horizontal page reader
        // TODO: Pinch-to-zoom
        // TODO: Minimal overlay (chapter title, page counter, settings)
        // TODO: Settings bottom sheet (direction, canvas theme, zoom)
        Text(
            text = "Reader: Media $mediaId, Chapter $chapterNum",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )
    }
}
