package ani.saikou.screens.player

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import ani.saikou.R
import ani.saikou.ui.theme.OnSurface
import ani.saikou.ui.theme.OnSurfaceVariant
import ani.saikou.ui.theme.Primary
import ani.saikou.ui.theme.SurfaceContainer
import io.github.theshid.prettylog.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import miyo.shared_ui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.androidx.compose.koinViewModel

/**
 * Tracks whether the branded intro has played this session.
 * Resets when the app process is killed.
 */
private var introShownThisSession = false

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun VideoPlayerScreen(
    mediaId: Int,
    episodeNum: Int,
    onBack: () -> Unit,
    onNextEpisode: ((Int) -> Unit)? = null,
    onNavigateToMedia: ((Int) -> Unit)? = null,
    viewModel: VideoPlayerViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerState by viewModel.uiState.collectAsState()

    // ── Settings sheet state ─────────────────────────────────
    var showSettingsSheet by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var aspectFill by remember { mutableStateOf(false) }

    // ── Intro state ──────────────────────────────────────────
    val shouldPlayIntro = !introShownThisSession
    var introActive by remember { mutableStateOf(shouldPlayIntro) }

    // Reset per episode — once a user dismisses the Up Next overlay we don't bring it back
    // until they navigate to a different episode.
    var upNextDismissed by remember(mediaId, episodeNum) { mutableStateOf(false) }
    val introAlpha = remember { Animatable(if (shouldPlayIntro) 1f else 0f) }

    // Full immersive mode — hides status bar + navigation bar completely
    DisposableEffect(Unit) {
        val activity = context as Activity
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        fun hideBars() {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }

        hideBars()
        // Re-apply on the next handler cycle: when navigating between episodes
        // the new screen's setup runs BEFORE the old screen's onDispose, so
        // its controller.show() would otherwise win and leave bars visible.
        window.decorView.post { hideBars() }

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    // ── Intro ExoPlayer ──────────────────────────────────────
    // Splash MP4 lives in :shared-ui/commonMain/composeResources/files/.
    // Res.getUri is suspend (lazy resource-index load) so we resolve it in
    // a LaunchedEffect and only build the player once the URI is known.
    var introUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(shouldPlayIntro) {
        if (shouldPlayIntro && introUri == null) {
            introUri = Res.getUri("files/splash_animation.mp4")
        }
    }
    val introPlayer =
        remember(introUri) {
            val uri = introUri
            if (shouldPlayIntro && uri != null) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = true
                    volume = 1f
                }
            } else {
                null
            }
        }

    // When intro video ends → crossfade out
    if (introPlayer != null) {
        DisposableEffect(introPlayer) {
            val listener =
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            scope.launch {
                                introAlpha.animateTo(0f, animationSpec = tween(400))
                                introActive = false
                                introShownThisSession = true
                            }
                        }
                    }
                }
            introPlayer.addListener(listener)
            onDispose {
                introPlayer.removeListener(listener)
                introPlayer.release()
            }
        }
    }

    // Skip intro on tap
    fun skipIntro() {
        if (!introActive) return
        introPlayer?.stop()
        scope.launch {
            introAlpha.animateTo(0f, animationSpec = tween(300))
            introActive = false
            introShownThisSession = true
        }
    }

    // ── Main ExoPlayer ───────────────────────────────────────
    // Custom DataSource.Factory so we can send Referer headers
    // (stream servers return 403 without it)
    val httpFactory =
        remember {
            DefaultHttpDataSource
                .Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
        }
    val exoPlayer =
        remember {
            // Media3 1.4+ disables legacy text decoding by default — TextRenderer
            // expects pre-parsed application/x-media3-cues samples. Sideloaded VTT
            // via SingleSampleMediaSource ships raw text/vtt samples, so we have
            // to re-enable legacy decoding or playback fails the moment a subtitle
            // track is selected. The toggle is per-TextRenderer in 1.5.1 (no
            // factory-level helper exists yet), so we subclass the factory.
            val renderersFactory =
                object : DefaultRenderersFactory(context) {
                    override fun buildTextRenderers(
                        context: android.content.Context,
                        output: TextOutput,
                        outputLooper: android.os.Looper,
                        extensionRendererMode: Int,
                        out: ArrayList<Renderer>,
                    ) {
                        out.add(
                            TextRenderer(output, outputLooper).apply {
                                experimentalSetLegacyDecodingEnabled(true)
                            },
                        )
                    }
                }
            ExoPlayer
                .Builder(context, renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
                .build()
        }

    // Load stream when available, seek to resume position
    LaunchedEffect(playerState.selectedLink) {
        playerState.selectedLink?.let { link ->
            Log.d("VideoPlayer", "── Stream link ──")
            Log.d("VideoPlayer", "  URL: ${link.url}")
            Log.d("VideoPlayer", "  Server: ${link.server}")
            Log.d("VideoPlayer", "  Headers: ${link.headers}")
            Log.d("VideoPlayer", "  Subtitles: ${link.subtitles.size} tracks")
            link.subtitles.forEachIndexed { i, sub ->
                Log.d("VideoPlayer", "    [$i] ${sub.label} (${sub.language}): ${sub.url}")
            }

            // Update headers for this stream (Referer required by CDN)
            httpFactory.setDefaultRequestProperties(link.headers)

            val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
            val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(link.url))

            if (link.subtitles.isNotEmpty()) {
                // Separate factory for subtitle CDN — no Referer header
                // (subtitle CDNs are different domains and reject the embed Referer)
                val subtitleFactory =
                    DefaultHttpDataSource
                        .Factory()
                        .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")

                val subtitleSources =
                    link.subtitles.mapNotNull { sub ->
                        try {
                            val subtitleConfig =
                                MediaItem.SubtitleConfiguration
                                    .Builder(Uri.parse(sub.url))
                                    .setMimeType(MimeTypes.TEXT_VTT)
                                    .setLanguage(sub.language)
                                    .setLabel(sub.label)
                                    .build()
                            val source =
                                SingleSampleMediaSource
                                    .Factory(subtitleFactory)
                                    .createMediaSource(subtitleConfig, C.TIME_UNSET)
                            Log.d("VideoPlayer", "  ✓ Created subtitle source: ${sub.label} → ${sub.url}")
                            source
                        } catch (e: Exception) {
                            Log.e("VideoPlayer", "  ✗ Failed subtitle source: ${sub.label}", e)
                            null
                        }
                    }
                if (subtitleSources.isNotEmpty()) {
                    Log.d("VideoPlayer", "  Merging ${subtitleSources.size} subtitle sources with video")
                    @Suppress("SpreadOperator") // Media3's MergingMediaSource constructor is varargs-only.
                    val merged = MergingMediaSource(videoSource, *subtitleSources.toTypedArray())
                    exoPlayer.setMediaSource(merged)
                } else {
                    Log.d("VideoPlayer", "  No valid subtitle sources — video only")
                    exoPlayer.setMediaSource(videoSource)
                }
            } else {
                Log.d("VideoPlayer", "  No subtitles found — video only")
                exoPlayer.setMediaSource(videoSource)
            }

            // Enable subtitle rendering if tracks are present
            if (link.subtitles.isNotEmpty()) {
                exoPlayer.trackSelectionParameters =
                    exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage("en")
                        .build()
                Log.d("VideoPlayer", "  Text tracks enabled, preferred language: en")
            }

            exoPlayer.prepare()
            if (playerState.resumePositionMs > 0) {
                exoPlayer.seekTo(playerState.resumePositionMs)
            }
            // If intro is playing, wait — otherwise play immediately
            if (!introActive) {
                exoPlayer.play()
            }
        }
    }

    // Start main video when intro finishes
    LaunchedEffect(introActive) {
        if (!introActive && playerState.selectedLink != null) {
            exoPlayer.play()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // Player state
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playWhenReady by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var showControls by remember { mutableStateOf(true) }

    // Keep screen on ONLY while video is playing — releases when paused/ended
    // so the phone can sleep if the user falls asleep
    DisposableEffect(isPlaying) {
        val window = (context as Activity).window
        if (isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // Update position periodically + report to ViewModel for history
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(1L)
            viewModel.onPositionChanged(currentPosition, duration)
            delay(500)
        }
    }

    // Track whether we already retried without subtitles
    var retriedWithoutSubs by remember { mutableStateOf(false) }

    // Listen to player state + handle errors
    DisposableEffect(exoPlayer) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                }

                override fun onPlayWhenReadyChanged(
                    value: Boolean,
                    reason: Int,
                ) {
                    playWhenReady = value
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val link = playerState.selectedLink ?: return
                    // If we had subtitles merged and haven't retried yet, retry video-only
                    if (!retriedWithoutSubs && link.subtitles.isNotEmpty()) {
                        Log.e("VideoPlayer", "Playback error with subtitles — retrying video-only", error)
                        retriedWithoutSubs = true
                        httpFactory.setDefaultRequestProperties(link.headers)
                        val fallback =
                            DefaultMediaSourceFactory(httpFactory)
                                .createMediaSource(MediaItem.fromUri(link.url))
                        exoPlayer.setMediaSource(fallback)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (introActive) {
                        skipIntro()
                    } else {
                        showControls = !showControls
                    }
                },
    ) {
        // Poster background — shows while stream is loading
        if (playerState.selectedLink == null && playerState.coverUrl != null) {
            coil3.compose.AsyncImage(
                model = playerState.coverUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.6f,
            )
        }

        // ExoPlayer surface (main video — buffers under the intro)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }
            },
            update = { view ->
                view.resizeMode =
                    if (aspectFill) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Branded Intro Overlay ────────────────────────────
        if (introActive && introPlayer != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(introAlpha.value),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = introPlayer
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
                    modifier = Modifier.fillMaxSize(),
                )

                // "Tap to skip" hint
                Text(
                    text = "Tap to skip",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp),
                )
            }
        }

        // Buffering spinner — shown when seeking or rebuffering (controls may be hidden).
        // 4 ANDed predicates is the minimum to express "actually buffering, not loading or paused".
        @Suppress("ComplexCondition")
        if (isBuffering && !introActive && !showControls && playerState.selectedLink != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
            }
        }

        // Loading animation while resolving stream (only show when intro is done)
        if (playerState.isLoading && !introActive && playerState.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ani.saikou.components.VideoLoader()
            }
        }

        // Error UI — shown whenever there's an error, regardless of loading state
        if (playerState.error != null && !introActive) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    coil3.compose.AsyncImage(
                        model = ani.saikou.R.drawable.error_samurai,
                        contentDescription = "Error",
                        modifier =
                            Modifier
                                .fillMaxWidth(0.7f)
                                .heightIn(max = 320.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                    Text(
                        text = playerState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier =
                                Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(SurfaceContainer)
                                    .clickable(onClick = onBack)
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Text("Go Back", color = OnSurface, fontWeight = FontWeight.Medium)
                        }
                        Box(
                            modifier =
                                Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(Primary)
                                    .clickable { viewModel.retry() }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Custom overlay controls (hidden during intro)
        AnimatedVisibility(
            visible = showControls && !introActive,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
            ) {
                // ── Top bar ──────────────────────────────────
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                                ),
                            ).padding(horizontal = 8.dp, vertical = 12.dp)
                            .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                        }
                        Column {
                            Text(
                                text = "${playerState.title} - Ep ${String.format(
                                    java.util.Locale.US,
                                    "%02d",
                                    episodeNum,
                                )}",
                                style = MaterialTheme.typography.titleSmall,
                                color = OnSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = playerState.episodeTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, "Settings", tint = OnSurface)
                    }
                }

                // ── Center controls ──────────────────────────
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasPrev = episodeNum > 1
                    val hasNext = playerState.totalEpisodes <= 0 || episodeNum < playerState.totalEpisodes

                    // Previous episode
                    if (hasPrev) {
                        IconButton(
                            onClick = {
                                exoPlayer.stop()
                                onNextEpisode?.invoke(episodeNum - 1)
                            },
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .background(SurfaceContainer.copy(alpha = 0.6f), CircleShape),
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                "Previous episode",
                                tint = OnSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    // Rewind 10s
                    IconButton(
                        onClick = { exoPlayer.seekBack() },
                        modifier =
                            Modifier
                                .size(48.dp)
                                .background(SurfaceContainer.copy(alpha = 0.6f), CircleShape),
                    ) {
                        Icon(Icons.Default.Replay10, "Rewind", tint = OnSurface, modifier = Modifier.size(28.dp))
                    }

                    // Play/Pause/Buffering
                    IconButton(
                        onClick = {
                            if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier =
                            Modifier
                                .size(64.dp)
                                .background(Primary, CircleShape),
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            Icon(
                                if (playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playWhenReady) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    // Forward 10s
                    IconButton(
                        onClick = { exoPlayer.seekForward() },
                        modifier =
                            Modifier
                                .size(48.dp)
                                .background(SurfaceContainer.copy(alpha = 0.6f), CircleShape),
                    ) {
                        Icon(Icons.Default.Forward10, "Forward", tint = OnSurface, modifier = Modifier.size(28.dp))
                    }

                    // Next episode
                    if (hasNext) {
                        IconButton(
                            onClick = {
                                exoPlayer.stop()
                                onNextEpisode?.invoke(episodeNum + 1)
                            },
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .background(SurfaceContainer.copy(alpha = 0.6f), CircleShape),
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                "Next episode",
                                tint = OnSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                // ── Bottom controls ──────────────────────────
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                ),
                            ).padding(horizontal = 16.dp, vertical = 12.dp)
                            .align(Alignment.BottomCenter),
                ) {
                    // Seekbar
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { fraction ->
                            exoPlayer.seekTo((fraction * duration).toLong())
                        },
                        colors =
                            SliderDefaults.colors(
                                thumbColor = Primary,
                                activeTrackColor = Primary,
                                inactiveTrackColor = OnSurfaceVariant.copy(alpha = 0.3f),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Time + action icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row {
                            Text(
                                text = formatTime(currentPosition),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface,
                            )
                            Text(
                                text = "  ${formatTime(duration)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else 1f
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector =
                                        if (isMuted) {
                                            Icons.AutoMirrored.Filled.VolumeOff
                                        } else {
                                            Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = if (isMuted) Primary else OnSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            val hasSubs = playerState.selectedLink?.subtitles?.isNotEmpty() == true
                            IconButton(
                                onClick = {
                                    if (!hasSubs) {
                                        Toast
                                            .makeText(
                                                context,
                                                "No subtitles available for this episode",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        return@IconButton
                                    }
                                    // Toggle text-track rendering on/off
                                    val trackParams = exoPlayer.trackSelectionParameters
                                    val currentlyEnabled =
                                        trackParams.overrides.none { (_, override) ->
                                            override.trackIndices.isEmpty()
                                        }
                                    val builder = trackParams.buildUpon()
                                    builder.setTrackTypeDisabled(
                                        androidx.media3.common.C.TRACK_TYPE_TEXT,
                                        currentlyEnabled,
                                    )
                                    exoPlayer.trackSelectionParameters = builder.build()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Subtitles,
                                    "Subtitles",
                                    tint = if (hasSubs) Primary else OnSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = { aspectFill = !aspectFill },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = if (aspectFill) "Fit to screen" else "Zoom to fill",
                                    tint = if (aspectFill) Primary else OnSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Skip Opening / Skip Ending button (AniSkip) ────────
        // Netflix-style: appears when entering the range, auto-hides after 6s
        // with a countdown progress bar. Tap skips to end of OP/ED.
        val skipTimes = playerState.skipTimes
        val currentSec = currentPosition / 1000f
        // Locals to enable smart-casts — SkipTimes lives in :data, so
        // direct property reads can't be narrowed across the module boundary.
        val opStart = skipTimes.opStartSec
        val opEnd = skipTimes.opEndSec
        val edStart = skipTimes.edStartSec
        val edEnd = skipTimes.edEndSec
        val inOpRange = opStart != null && opEnd != null && currentSec >= opStart && currentSec < opEnd
        val inEdRange = edStart != null && edEnd != null && currentSec >= edStart && currentSec < edEnd
        // Only activate skip when video is actually playing (not during intro or buffering)
        val inSkipRange = (inOpRange || inEdRange) && !introActive && isPlaying

        // Track which range we've already shown/dismissed the button for
        var skipDismissedForOp by remember { mutableStateOf(false) }
        var skipDismissedForEd by remember { mutableStateOf(false) }
        val alreadyDismissed = (inOpRange && skipDismissedForOp) || (inEdRange && skipDismissedForEd)

        // Reset dismissed flag when leaving a range
        LaunchedEffect(inOpRange) { if (!inOpRange) skipDismissedForOp = false }
        LaunchedEffect(inEdRange) { if (!inEdRange) skipDismissedForEd = false }

        // Countdown progress (1f → 0f over 6 seconds)
        val skipProgress = remember { Animatable(1f) }
        var skipVisible by remember { mutableStateOf(false) }

        LaunchedEffect(inSkipRange, alreadyDismissed) {
            if (inSkipRange && !alreadyDismissed) {
                skipVisible = true
                skipProgress.snapTo(1f)
                skipProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 15000, easing = LinearEasing),
                )
                // Auto-hide after countdown
                skipVisible = false
                if (inOpRange) skipDismissedForOp = true
                if (inEdRange) skipDismissedForEd = true
            } else if (!inSkipRange) {
                skipVisible = false
            }
        }

        AnimatedVisibility(
            visible = skipVisible,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 100.dp),
        ) {
            val label = if (inOpRange) "Skip Opening" else "Next Episode"
            Box(
                modifier =
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            skipVisible = false
                            if (inOpRange) {
                                // Skip opening → seek to end of OP
                                exoPlayer.seekTo((skipTimes.opEndSec!! * 1000).toLong())
                                skipDismissedForOp = true
                            } else {
                                // Skip ending → navigate to next episode
                                skipDismissedForEd = true
                                val nextEp = episodeNum + 1
                                if (onNextEpisode != null) {
                                    exoPlayer.stop()
                                    onNextEpisode(nextEp)
                                } else {
                                    exoPlayer.seekTo((skipTimes.edEndSec!! * 1000).toLong())
                                }
                            }
                            showControls = false
                        },
            ) {
                // Button with fixed intrinsic size from the text content
                Box(
                    modifier =
                        Modifier
                            .background(Primary, MaterialTheme.shapes.small),
                ) {
                    // Countdown progress overlay
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .clip(MaterialTheme.shapes.small)
                                .background(Color.Black.copy(alpha = 0.3f)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .clip(MaterialTheme.shapes.small),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(skipProgress.value)
                                    .background(Color.White.copy(alpha = 0.15f)),
                        )
                    }
                    // Label — this drives the button size
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // ── Up Next overlay (end-of-season recommendations) ─────
        // Appears in the final 15% of the last episode — like Netflix post-play.
        val isLastEpisode = playerState.totalEpisodes > 0 && episodeNum >= playerState.totalEpisodes
        val nearEnd = duration > 0 && (currentPosition.toFloat() / duration) >= 0.85f
        val showUpNext =
            isLastEpisode &&
                nearEnd &&
                isPlaying &&
                playerState.recommendations.isNotEmpty() &&
                !introActive &&
                !upNextDismissed

        AnimatedVisibility(
            visible = showUpNext,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            UpNextOverlay(
                finishedTitle = playerState.title,
                recommendations = playerState.recommendations,
                onMediaClick = { id ->
                    exoPlayer.pause()
                    onNavigateToMedia?.invoke(id)
                },
                onDismiss = { upNextDismissed = true },
            )
        }
    }

    // Source selector bottom sheet
    if (playerState.showSourceSelector) {
        ani.saikou.components.SourceSelectorSheet(
            title = "Select Source",
            sources = playerState.availableSources,
            onSelect = { source ->
                viewModel.selectSourceById(source.id)
            },
            onDismiss = { viewModel.dismissSourceSelector() },
        )
    }

    // Player settings sheet (currently: playback speed)
    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceContainer,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Playback speed",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    speeds.forEach { speed ->
                        val selected = speed == playbackSpeed
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) Primary.copy(alpha = 0.18f) else Color.Transparent,
                                    ).clickable {
                                        playbackSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        scope.launch {
                                            sheetState.hide()
                                            showSettingsSheet = false
                                        }
                                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (speed == 1f) "1x (Normal)" else "${speed}x",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) Primary else OnSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun UpNextOverlay(
    finishedTitle: String,
    recommendations: List<ani.saikou.domain.model.Media>,
    onMediaClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.95f),
                            ),
                        startY = 0f,
                    ),
                ),
    ) {
        // Close button — sits in the bottom-area gradient (top of screen has playback
        // controls, so a corner X up there would conflict with them).
        IconButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Dismiss recommendations",
                tint = Color.White,
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "You finished $finishedTitle",
                style = MaterialTheme.typography.titleSmall,
                color = OnSurfaceVariant,
                fontWeight = FontWeight.Normal,
            )
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = recommendations.size,
                    key = { i -> recommendations[i].id },
                ) { i ->
                    val media = recommendations[i]
                    UpNextCard(
                        media = media,
                        onClick = { onMediaClick(media.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpNextCard(
    media: ani.saikou.domain.model.Media,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(130.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 130.dp, height = 180.dp)
                    .clip(MaterialTheme.shapes.medium),
        ) {
            coil3.compose.AsyncImage(
                model = media.cover,
                contentDescription = media.displayTitle,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            media.meanScore?.takeIf { it > 0 }?.let { score ->
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "★ ${score / 10.0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                    )
                }
            }
        }
        Text(
            text = media.displayTitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
    }
}
