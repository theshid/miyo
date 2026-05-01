package ani.saikou.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.components.SourceItem
import ani.saikou.data.local.ListEvent
import ani.saikou.data.local.ListEventBus
import ani.saikou.data.local.db.ActivityEventDao
import ani.saikou.data.local.db.ActivityEventEntity
import ani.saikou.data.local.db.WatchHistoryDao
import ani.saikou.data.local.db.WatchHistoryEntity
import ani.saikou.data.remote.AniSkipApi
import ani.saikou.data.remote.SkipTimes
import ani.saikou.data.remote.parsers.GogoParser
import ani.saikou.domain.model.AnimeSource
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.repository.AnilistRepository
import io.github.theshid.prettylog.Log
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VideoPlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: AnilistRepository,
    private val watchHistoryDao: WatchHistoryDao,
    private val activityDao: ActivityEventDao,
) : ViewModel() {
    private val gogoParser = GogoParser()
    private val aniSkipApi = AniSkipApi()

    val mediaId: Int = savedStateHandle["mediaId"] ?: 0
    val episodeNum: Int = savedStateHandle["episodeNum"] ?: 1
    private val navSourceSlug: String? = savedStateHandle.get<String>("sourceSlug")?.takeIf { it.isNotEmpty() }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var animeSources: List<AnimeSource> = emptyList()
    private var resolvedSourceSlug: String? = null
    private var saveJob: Job? = null
    private var anilistProgressSynced = false
    private var firstSaveDone = false

    init {
        loadSources()
    }

    private fun loadSources() {
        // Reset error state on retry
        _uiState.value = _uiState.value.copy(error = null)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val media = repository.getMedia(mediaId)
            val title = media?.nameRomaji ?: media?.name ?: "Unknown"

            _uiState.value =
                _uiState.value.copy(
                    title = title,
                    episodeTitle = "Episode $episodeNum",
                    coverUrl = media?.banner ?: media?.cover,
                    totalEpisodes = media?.totalEpisodes ?: 0,
                    recommendations = media?.recommendations.orEmpty().take(10),
                )

            // Fetch skip times from AniSkip (non-blocking — runs concurrently)
            media?.malId?.let { malId ->
                viewModelScope.launch {
                    val skipTimes = aniSkipApi.getSkipTimes(malId, episodeNum)
                    _uiState.value = _uiState.value.copy(skipTimes = skipTimes)
                }
            }

            // If source slug was passed via navigation (user already picked), use it directly
            if (navSourceSlug != null) {
                resolvedSourceSlug = navSourceSlug
                val history = watchHistoryDao.getForMedia(mediaId)
                val resumePosition = if (history != null && history.episodeNumber == episodeNum) history.lastPositionMs else 0L
                _uiState.value = _uiState.value.copy(resumePositionMs = resumePosition)
                loadEpisodeFromSource(navSourceSlug)
                return@launch
            }

            // Check watch history for saved source — skip search if found
            val history = watchHistoryDao.getForMedia(mediaId)
            if (history != null && history.sourceSlug.isNotEmpty()) {
                resolvedSourceSlug = history.sourceSlug
                val resumePosition = if (history.episodeNumber == episodeNum) history.lastPositionMs else 0L
                _uiState.value = _uiState.value.copy(resumePositionMs = resumePosition)
                loadEpisodeFromSource(history.sourceSlug)
                return@launch
            }

            // No history — search Gogo
            animeSources = gogoParser.search(title)
            if (animeSources.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Anime not found on source")
                reportPlayerError("Anime not found on source")
                return@launch
            }

            if (animeSources.size == 1) {
                selectSource(animeSources.first())
            } else {
                _uiState.value =
                    _uiState.value.copy(
                        showSourceSelector = true,
                        availableSources =
                            animeSources.map {
                                SourceItem(id = it.slug, title = it.name, coverUrl = it.cover)
                            },
                        isLoading = false,
                    )
            }
        }
    }

    fun selectSource(source: AnimeSource) {
        resolvedSourceSlug = source.slug
        _uiState.value = _uiState.value.copy(showSourceSelector = false, isLoading = true)
        viewModelScope.launch {
            loadEpisodeFromSource(source.slug)
        }
    }

    private suspend fun loadEpisodeFromSource(slug: String) {
        val episodes = gogoParser.getEpisodes(slug)
        val episode = episodes.find { it.number == episodeNum.toString() }
        val link = episode?.link
        if (link == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "Episode $episodeNum not found")
            reportPlayerError("Episode not found on source")
            return
        }

        val links = gogoParser.getStreamLinks(link)
        _uiState.value =
            _uiState.value.copy(
                streamLinks = links,
                selectedLink = links.firstOrNull(),
                isLoading = false,
            )
    }

    fun selectSourceById(id: String) {
        val source = animeSources.find { it.slug == id } ?: return
        selectSource(source)
    }

    fun selectStream(link: StreamLink) {
        _uiState.value = _uiState.value.copy(selectedLink = link)
    }

    fun dismissSourceSelector() {
        _uiState.value = _uiState.value.copy(showSourceSelector = false)
        animeSources.firstOrNull()?.let { selectSource(it) }
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(error = null, isLoading = true)
        loadSources()
    }

    private var lastPositionMs = 0L
    private var lastDurationMs = 0L

    /**
     * Called from the player screen periodically with current playback position.
     * Local history save is debounced (5s). AniList sync fires immediately at 80%.
     */
    fun onPositionChanged(
        positionMs: Long,
        durationMs: Long,
    ) {
        lastPositionMs = positionMs
        lastDurationMs = durationMs

        // Sync to AniList immediately when 80% threshold is crossed (not debounced)
        if (durationMs > 0 && !anilistProgressSynced) {
            val fraction = positionMs.toFloat() / durationMs
            if (fraction >= 0.80f) {
                anilistProgressSynced = true
                viewModelScope.launch {
                    try {
                        repository.editListEntry(
                            mediaId = mediaId,
                            progress = episodeNum,
                            status = "CURRENT",
                        )
                        Log.i(tag = "AniSync", message = "Synced progress to AniList: $mediaId ep $episodeNum")
                        ListEventBus.emit(ListEvent.ProgressUpdated(mediaId, episodeNum))
                    } catch (e: Exception) {
                        Log.e(tag = "AniSync", message = "Failed to sync progress", throwable = e)
                        anilistProgressSynced = false
                    }
                }
            }
        }

        // Save local history: immediate on first update, debounced after
        saveJob?.cancel()
        if (!firstSaveDone) {
            firstSaveDone = true
            viewModelScope.launch {
                val state = _uiState.value
                activityDao.insert(
                    ActivityEventEntity(
                        timestampMs = System.currentTimeMillis(),
                        type = "watch",
                        mediaId = mediaId,
                        mediaTitle = state.title,
                        coverUrl = state.coverUrl,
                        episodeNumber = episodeNum,
                    ),
                )
                saveLocalProgress(positionMs, durationMs)
            }
        } else {
            saveJob =
                viewModelScope.launch {
                    delay(5000)
                    saveLocalProgress(positionMs, durationMs)
                }
        }
    }

    private suspend fun saveLocalProgress(
        positionMs: Long,
        durationMs: Long,
    ) {
        val state = _uiState.value
        val slug = resolvedSourceSlug ?: return
        if (durationMs <= 0) return

        // Read current stored value so completedEpisodes only ever goes UP
        val existing = watchHistoryDao.getForMedia(mediaId)
        val prevCompleted = existing?.completedEpisodes ?: 0

        val fraction = positionMs.toFloat() / durationMs
        val completed =
            if (fraction >= 0.80f) {
                maxOf(prevCompleted, episodeNum)
            } else {
                prevCompleted
            }

        watchHistoryDao.upsert(
            WatchHistoryEntity(
                mediaId = mediaId,
                mediaTitle = state.title,
                coverUrl = state.coverUrl,
                episodeNumber = episodeNum,
                sourceSlug = slug,
                sourceName = "Gogo",
                lastPositionMs = positionMs,
                durationMs = durationMs,
                completedEpisodes = completed,
                lastWatchedAt = System.currentTimeMillis(),
            ),
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("GlobalCoroutineUsage") // viewModelScope is cancelled in onCleared; we need a process-scoped final save.
    override fun onCleared() {
        // Best-effort final save — fire-and-forget on a global IO scope so we
        // don't block the main thread or risk ANR. The viewModelScope is already
        // cancelled at this point, so we use a one-shot launch.
        val pos = lastPositionMs
        val dur = lastDurationMs
        if (dur > 0) {
            GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    saveLocalProgress(pos, dur)
                } catch (_: Exception) {
                    // best-effort
                }
            }
        }
        saveJob?.cancel()
        super.onCleared()
    }

    /** Report a non-fatal player error to Sentry with the context needed to debug it. */
    private fun reportPlayerError(message: String) {
        try {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setTag("area", "VideoPlayer")
                scope.setTag("mediaId", mediaId.toString())
                scope.setTag("episode", episodeNum.toString())
                scope.setExtra("title", _uiState.value.title)
                scope.setExtra("resolvedSourceSlug", resolvedSourceSlug ?: "")
                Sentry.captureMessage(message)
            }
        } catch (_: Exception) {
            // best-effort
        }
    }
}

data class PlayerUiState(
    val title: String = "",
    val episodeTitle: String = "",
    val coverUrl: String? = null,
    val totalEpisodes: Int = 0,
    val recommendations: List<Media> = emptyList(),
    val streamLinks: List<StreamLink> = emptyList(),
    val selectedLink: StreamLink? = null,
    val availableSources: List<SourceItem> = emptyList(),
    val showSourceSelector: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val resumePositionMs: Long = 0L,
    val skipTimes: SkipTimes = SkipTimes.EMPTY,
)
