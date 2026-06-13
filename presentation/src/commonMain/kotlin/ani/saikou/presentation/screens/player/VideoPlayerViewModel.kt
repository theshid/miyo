package ani.saikou.presentation.screens.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.event.ListEvent
import ani.saikou.domain.event.ListEventBus
import ani.saikou.domain.model.ActivityEvent
import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.SkipTimes
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.model.WatchHistoryItem
import ani.saikou.domain.model.anime.AnimeSourceFailure
import ani.saikou.domain.model.anime.AnimeSourceResult
import ani.saikou.domain.model.anime.LoadedEpisodeStream
import ani.saikou.domain.usecase.activity.RecordActivityEventUseCase
import ani.saikou.domain.usecase.anilist.EditListEntryUseCase
import ani.saikou.domain.usecase.anilist.GetMediaDetailUseCase
import ani.saikou.domain.usecase.anime.GetEpisodeSkipTimesUseCase
import ani.saikou.domain.usecase.anime.LoadEpisodeStreamUseCase
import ani.saikou.domain.usecase.anime.ResolveAnimeSourcesUseCase
import ani.saikou.domain.usecase.history.GetWatchHistoryForMediaUseCase
import ani.saikou.domain.usecase.history.UpsertWatchHistoryUseCase
import ani.saikou.platform.log.Logger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class VideoPlayerViewModel(
    savedStateHandle: SavedStateHandle,
    private val getMediaDetail: GetMediaDetailUseCase,
    private val getSkipTimes: GetEpisodeSkipTimesUseCase,
    private val resolveAnimeSources: ResolveAnimeSourcesUseCase,
    private val loadEpisodeStream: LoadEpisodeStreamUseCase,
    private val getWatchHistoryFor: GetWatchHistoryForMediaUseCase,
    private val upsertWatchHistory: UpsertWatchHistoryUseCase,
    private val recordActivityEvent: RecordActivityEventUseCase,
    private val editListEntry: EditListEntryUseCase,
    private val logger: Logger,
) : ViewModel() {
    val mediaId: Int = savedStateHandle["mediaId"] ?: 0
    val episodeNum: Int = savedStateHandle["episodeNum"] ?: 1
    private val navSourceSlug: String? = savedStateHandle.get<String>("sourceSlug")?.takeIf { it.isNotEmpty() }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var animeSources: List<AnimeSearchResult> = emptyList()
    private var resolvedSourceSlug: String? = null
    private var saveJob: Job? = null
    private var anilistProgressSynced = false
    private var firstSaveDone = false

    init {
        loadSources()
    }

    private fun loadSources() {
        _uiState.update { it.copy(error = null) }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val media = getMediaDetail(mediaId)
            val title = media?.nameRomaji ?: media?.name ?: "Unknown"

            _uiState.update {
                it.copy(
                    title = title,
                    episodeTitle = "Episode $episodeNum",
                    coverUrl = media?.banner ?: media?.cover,
                    totalEpisodes = media?.totalEpisodes ?: 0,
                    recommendations = media?.recommendations.orEmpty().take(10),
                )
            }

            // Fetch skip times from AniSkip (non-blocking — runs concurrently).
            media?.malId?.let { malId ->
                viewModelScope.launch {
                    val skipTimes = getSkipTimes(malId, episodeNum)
                    _uiState.update { it.copy(skipTimes = skipTimes) }
                }
            }

            // If source slug was passed via navigation (user already picked), use it directly.
            if (navSourceSlug != null) {
                resolvedSourceSlug = navSourceSlug
                val history = getWatchHistoryFor(mediaId)
                val resumePosition = if (history != null && history.episodeNumber == episodeNum) history.lastPositionMs else 0L
                _uiState.update { it.copy(resumePositionMs = resumePosition) }
                loadEpisodeFromSource(navSourceSlug)
                return@launch
            }

            // Check watch history for saved source — skip search if found.
            val history = getWatchHistoryFor(mediaId)
            if (history != null && history.sourceSlug.isNotEmpty()) {
                resolvedSourceSlug = history.sourceSlug
                val resumePosition = if (history.episodeNumber == episodeNum) history.lastPositionMs else 0L
                _uiState.update { it.copy(resumePositionMs = resumePosition) }
                loadEpisodeFromSource(history.sourceSlug)
                return@launch
            }

            // No history — search the source catalog.
            animeSources =
                when (val outcome = resolveAnimeSources(title)) {
                    is AnimeSourceResult.Failed -> {
                        val message = friendlySourceMessage(outcome.failure)
                        _uiState.update { it.copy(isLoading = false, error = message) }
                        reportPlayerError("resolveAnimeSources: ${outcome.failure::class.simpleName}")
                        return@launch
                    }
                    is AnimeSourceResult.Success -> outcome.value
                }
            if (animeSources.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, error = "Anime not found on source") }
                reportPlayerError("Anime not found on source")
                return@launch
            }

            if (animeSources.size == 1) {
                selectSource(animeSources.first())
            } else {
                _uiState.update {
                    it.copy(
                        showSourceSelector = true,
                        availableSources = animeSources,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun selectSource(source: AnimeSearchResult) {
        resolvedSourceSlug = source.slug
        _uiState.update { it.copy(showSourceSelector = false, isLoading = true) }
        viewModelScope.launch { loadEpisodeFromSource(source.slug) }
    }

    private suspend fun loadEpisodeFromSource(slug: String) {
        when (val outcome = loadEpisodeStream(slug, episodeNum)) {
            is AnimeSourceResult.Failed -> {
                val message = friendlySourceMessage(outcome.failure)
                _uiState.update { it.copy(isLoading = false, error = message) }
                reportPlayerError("loadEpisodeStream: ${outcome.failure::class.simpleName}")
            }
            is AnimeSourceResult.Success ->
                when (val loaded = outcome.value) {
                    is LoadedEpisodeStream.EpisodeNotFound -> {
                        _uiState.update { it.copy(isLoading = false, error = "Episode $episodeNum not found") }
                        reportPlayerError("Episode not found on source")
                    }
                    is LoadedEpisodeStream.Available -> {
                        if (loaded.links.isEmpty()) {
                            _uiState.update { it.copy(isLoading = false, error = "No playable streams found") }
                            reportPlayerError("No playable streams extracted from embeds")
                        } else {
                            _uiState.update {
                                it.copy(
                                    streamLinks = loaded.links,
                                    selectedLink = loaded.links.firstOrNull(),
                                    isLoading = false,
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * Maps a typed source failure to the copy the player surfaces. Specific
     * messages are favoured over a single generic one so the user knows
     * whether to retry, try another source, or wait it out.
     */
    private fun friendlySourceMessage(failure: AnimeSourceFailure): String =
        when (failure) {
            is AnimeSourceFailure.Blocked ->
                "Anime source is temporarily blocking app access. This isn't specific to this title — try again later."
            is AnimeSourceFailure.Unavailable ->
                "Anime source is temporarily down. Try again in a few minutes."
            is AnimeSourceFailure.TransportError ->
                "Network problem reaching the anime source. Check your connection."
            is AnimeSourceFailure.ContractChanged ->
                "Anime source changed its page format. We're working on a fix."
        }

    fun selectSourceById(id: String) {
        val source = animeSources.find { it.slug == id } ?: return
        selectSource(source)
    }

    fun selectStream(link: StreamLink) {
        _uiState.update { it.copy(selectedLink = link) }
    }

    fun dismissSourceSelector() {
        _uiState.update { it.copy(showSourceSelector = false) }
        animeSources.firstOrNull()?.let { selectSource(it) }
    }

    fun retry() {
        _uiState.update { it.copy(error = null, isLoading = true) }
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

        // Sync to AniList immediately when 80% threshold is crossed (not debounced).
        if (durationMs > 0 && !anilistProgressSynced) {
            val fraction = positionMs.toFloat() / durationMs
            if (fraction >= ANILIST_PROGRESS_FRACTION) {
                anilistProgressSynced = true
                viewModelScope.launch {
                    try {
                        editListEntry(mediaId = mediaId, progress = episodeNum, status = "CURRENT")
                        ListEventBus.emit(ListEvent.ProgressUpdated(mediaId, episodeNum))
                    } catch (e: Exception) {
                        // Don't latch progressSynced — let the next tick retry.
                        logger.reportError(
                            area = "VideoPlayer",
                            method = "anilistSync",
                            throwable = e,
                            extras = mapOf("mediaId" to mediaId.toString(), "episode" to episodeNum.toString()),
                        )
                        anilistProgressSynced = false
                    }
                }
            }
        }

        // Save local history: immediate on first update, debounced after.
        saveJob?.cancel()
        if (!firstSaveDone) {
            firstSaveDone = true
            viewModelScope.launch {
                val state = _uiState.value
                recordActivityEvent(
                    ActivityEvent(
                        timestampMs = Clock.System.now().toEpochMilliseconds(),
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
                    delay(SAVE_DEBOUNCE_MS)
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

        // Read current stored value so completedEpisodes only ever goes UP.
        val existing = getWatchHistoryFor(mediaId)
        val prevCompleted = existing?.completedEpisodes ?: 0

        val fraction = positionMs.toFloat() / durationMs
        val completed =
            if (fraction >= ANILIST_PROGRESS_FRACTION) {
                maxOf(prevCompleted, episodeNum)
            } else {
                prevCompleted
            }

        upsertWatchHistory(
            WatchHistoryItem(
                mediaId = mediaId,
                mediaTitle = state.title,
                coverUrl = state.coverUrl,
                episodeNumber = episodeNum,
                sourceSlug = slug,
                sourceName = "Gogo",
                lastPositionMs = positionMs,
                durationMs = durationMs,
                completedEpisodes = completed,
                lastWatchedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("GlobalCoroutineUsage") // viewModelScope is cancelled in onCleared; we need a process-scoped final save.
    override fun onCleared() {
        // Best-effort final save — fire-and-forget on a global scope so
        // we don't block the main thread or risk ANR. The viewModelScope
        // is already cancelled at this point.
        val pos = lastPositionMs
        val dur = lastDurationMs
        if (dur > 0) {
            GlobalScope.launch {
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

    private fun reportPlayerError(message: String) {
        logger.reportWarning(
            area = "VideoPlayer",
            method = "loadSources",
            message = message,
            extras =
                mapOf(
                    "mediaId" to mediaId.toString(),
                    "episode" to episodeNum.toString(),
                    "title" to _uiState.value.title,
                    "resolvedSourceSlug" to (resolvedSourceSlug ?: ""),
                ),
        )
    }

    companion object {
        /** Fraction of the episode after which we treat it as "watched enough" to sync. */
        private const val ANILIST_PROGRESS_FRACTION = 0.80f
        private const val SAVE_DEBOUNCE_MS = 5_000L
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
    /** Sources the user can pick from when no slug was passed via navigation. */
    val availableSources: List<AnimeSearchResult> = emptyList(),
    val showSourceSelector: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val resumePositionMs: Long = 0L,
    val skipTimes: SkipTimes = SkipTimes.EMPTY,
)
