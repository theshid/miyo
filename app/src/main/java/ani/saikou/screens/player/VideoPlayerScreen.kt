package ani.saikou.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ani.saikou.ui.theme.OnSurface

@Composable
fun VideoPlayerScreen(
    mediaId: Int,
    episodeNum: Int,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // TODO: ExoPlayer fullscreen
        // TODO: Custom overlay controls (play/pause, seek, quality, subtitles)
        // TODO: Swipe gestures (volume/brightness)
        Text(
            text = "Player: Media $mediaId, Episode $episodeNum",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )
    }
}
