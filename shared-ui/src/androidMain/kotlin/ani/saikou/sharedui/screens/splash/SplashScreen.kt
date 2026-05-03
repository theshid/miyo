package ani.saikou.sharedui.screens.splash

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import miyo.shared_ui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Splash screen that plays the logo animation MP4, fades out smoothly,
 * then notifies the caller via [onSplashComplete].
 *
 * Resource is resolved through the Compose Multiplatform `Res` accessor
 * generated from `:shared-ui/src/commonMain/composeResources/files/`. On
 * Android `Res.getUri(...)` returns a `file:///android_asset/...` URI that
 * ExoPlayer can play directly. When iOS targets are added, the equivalent
 * call will resolve to the native bundle path via the same accessor — only
 * the player implementation needs an `expect/actual` split.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fadeAlpha = remember { Animatable(1f) }
    var videoEnded by remember { mutableStateOf(false) }
    var splashUri by remember { mutableStateOf<String?>(null) }

    // Res.getUri is suspend (it lazy-loads the resource index), so resolve
    // the URI in a LaunchedEffect and only build the player once it's known.
    LaunchedEffect(Unit) {
        splashUri = Res.getUri("files/splash_animation.mp4")
    }

    val uri = splashUri ?: return

    val exoPlayer =
        remember(uri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
                volume = 1f
            }
        }

    DisposableEffect(exoPlayer) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && !videoEnded) {
                        videoEnded = true
                        scope.launch {
                            fadeAlpha.animateTo(
                                0f,
                                animationSpec = tween(durationMillis = 600),
                            )
                            onSplashComplete()
                        }
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(fadeAlpha.value),
        )
    }
}
