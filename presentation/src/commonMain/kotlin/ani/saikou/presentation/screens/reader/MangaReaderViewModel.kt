package ani.saikou.presentation.screens.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.event.ListEvent
import ani.saikou.domain.event.ListEventBus
import ani.saikou.domain.model.ActivityEvent
import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.DownloadRequest
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSearchResult
import ani.saikou.domain.model.ReadingHistoryItem
import ani.saikou.domain.usecase.activity.RecordActivityEventUseCase
import ani.saikou.domain.usecase.anilist.EditListEntryUseCase
import ani.saikou.domain.usecase.anilist.GetMediaDetailUseCase
import ani.saikou.domain.usecase.downloads.CancelChapterByNumberUseCase
import ani.saikou.domain.usecase.downloads.CancelChapterDownloadUseCase
import ani.saikou.domain.usecase.downloads.CleanupPhantomDownloadsUseCase
import ani.saikou.domain.usecase.downloads.EstimateNextChaptersBytesUseCase
import ani.saikou.domain.usecase.downloads.GetCompletedChapterUseCase
import ani.saikou.domain.usecase.downloads.GetLocalPagesUseCase
import ani.saikou.domain.usecase.downloads.ObserveChapterDownloadsForMangaUseCase
import ani.saikou.domain.usecase.downloads.QueueChapterDownloadUseCase
import ani.saikou.domain.usecase.downloads.QueueNextChaptersUseCase
import ani.saikou.domain.usecase.history.GetReadingHistoryForMediaUseCase
import ani.saikou.domain.usecase.history.UpsertReadingHistoryUseCase
import ani.saikou.domain.usecase.manga.GetChapterPagesUseCase
import ani.saikou.domain.usecase.manga.GetChaptersForSourceUseCase
import ani.saikou.domain.usecase.manga.ResolveChaptersForMangaUseCase
import ani.saikou.domain.usecase.manga.ResolveMangaSourcesUseCase
import ani.saikou.platform.log.Logger
import ani.saikou.presentation.screens.detail.ChapterDownloadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class MangaReaderViewModel(
    savedStateHandle: SavedStateHandle,
    private val getMediaDetail: GetMediaDetailUseCase,
    private val editListEntry: EditListEntryUseCase,
    private val getReadingHistoryFor: GetReadingHistoryForMediaUseCase,
    private val upsertReadingHistory: UpsertReadingHistoryUseCase,
    private val recordActivityEvent: RecordActivityEventUseCase,
    private val resolveMangaSources: ResolveMangaSourcesUseCase,
    private val resolveChaptersForManga: ResolveChaptersForMangaUseCase,
    private val getChaptersForSource: GetChaptersForSourceUseCase,
    private val getChapterPages: GetChapterPagesUseCase,
    private val getCompletedChapter: GetCompletedChapterUseCase,
    private val getLocalPages: GetLocalPagesUseCase,
    private val estimateNextChaptersBytes: EstimateNextChaptersBytesUseCase,
    private val queueChapter: QueueChapterDownloadUseCase,
    private val queueNextChaptersUseCase: QueueNextChaptersUseCase,
    private val cancelChapter: CancelChapterDownloadUseCase,
    private val cancelChapterByNumber: CancelChapterByNumberUseCase,
    private val cleanupPhantomDownloads: CleanupPhantomDownloadsUseCase,
    observeChapterDownloads: ObserveChapterDownloadsForMangaUseCase,
    private val logger: Logger,
) : ViewModel() {
    private var activeParser: String = SOURCE_MANGA_DEX

    val mediaId: Int = savedStateHandle["mediaId"] ?: 0
    val chapterNum: Int = savedStateHandle["chapterNum"] ?: 1
    private val navSourceId: String? = savedStateHandle.get<String>("sourceId")?.takeIf { it.isNotEmpty() }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    /** Full chapter list for the current source — lazy-loaded on first access. */
    private val _allChapters = MutableStateFlow<List<Chapter>>(emptyList())
    val allChapters: StateFlow<List<Chapter>> = _allChapters

    private val _chapterListLoading = MutableStateFlow(false)
    val chapterListLoading: StateFlow<Boolean> = _chapterListLoading

    /** Reactive map of chapterNumber → download state for this manga. */
    val chapterDownloads: StateFlow<Map<Int, ChapterDownloadState>> =
        observeChapterDownloads(mediaId)
            .map { downloads ->
                downloads.mapValues { (_, d) ->
                    ChapterDownloadState(
                        status = d.status.name,
                        downloadedPages = d.downloadedPages,
                        totalPages = d.totalPages,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var mangaSources: List<MangaSearchResult> = emptyList()
    private var resolvedSourceId: String? = null
        set(value) {
            field = value
            _uiState.value = _uiState.value.copy(resolvedSourceId = value)
        }
    private var resolvedChapterId: String? = null
    private var coverUrl: String? = null
    private var saveJob: Job? = null
    private var anilistProgressSynced = false

    init {
        loadSources()
    }

    /**
     * Populate [allChapters] for the chapter picker. If we already have a
     * resolved source id, hit it directly. Otherwise fall back to a
     * title-keyed resolve so the sheet still gets a list. No-op when
     * already loaded; safe to call from UI.
     */
    fun ensureChapterListLoaded() {
        if (_allChapters.value.isNotEmpty() || _chapterListLoading.value) return
        viewModelScope.launch { loadChapterListNow() }
    }

    private suspend fun loadChapterListNow() {
        if (_allChapters.value.isNotEmpty()) return
        _chapterListLoading.value = true
        val chapters =
            try {
                val sid = resolvedSourceId?.takeIf { it.isNotEmpty() }
                if (sid != null) {
                    getChaptersForSource(sid)
                } else {
                    val resolved = resolveChaptersForManga(_uiState.value.title)
                    if (resolved != null) {
                        activeParser = resolved.sourceName
                        resolvedSourceId = resolved.sourceMangaId
                        resolved.chapters
                    } else {
                        emptyList()
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        _allChapters.value = chapters
        _chapterListLoading.value = false
        purgePhantomDownloadsFor(chapters)
    }

    /**
     * Best-effort sweep of stranded download rows for any chapter number
     * past the source's known max. Called whenever a fresh chapter list is
     * resolved — backfills the fix for the pre-clamp `QueueNextChaptersUseCase`,
     * so users who already have phantom 221/222/223 rows from before don't
     * keep seeing them in the picker. No-op on empty input (avoids deleting
     * legitimate downloads if the source temporarily returns nothing).
     */
    private suspend fun purgePhantomDownloadsFor(chapters: List<Chapter>) {
        if (chapters.isEmpty()) return
        val maxKnown = chapters.maxOf { it.number.toInt() }
        runCatching { cleanupPhantomDownloads(mediaId, maxKnown) }
    }

    fun cancelDownload(chapterNumber: Int) {
        // Looks the row up by chapter number, so it works whether the row's
        // chapterKey is a real source-side id (queue-next path / single-queue
        // post-fix) or a legacy stringified chapter number.
        viewModelScope.launch { cancelChapterByNumber(mediaId, chapterNumber) }
    }

    fun queueSingleChapterDownload(
        chapterNumber: Int,
        onQueued: () -> Unit = {},
    ) {
        val state = _uiState.value
        viewModelScope.launch {
            // Mirror the queue-next fix: look up the chapter in the source's
            // real list so DownloadService can fetch pages directly via the
            // parser id. Falls back to the legacy synthetic key when the list
            // isn't available — preserves single-chapter download for screens
            // that haven't loaded the picker yet.
            loadChapterListNow()
            val chapter = _allChapters.value.firstOrNull { it.number.toInt() == chapterNumber }
            queueChapter(
                DownloadRequest(
                    mangaId = mediaId,
                    mangaTitle = state.title,
                    coverUrl = coverUrl,
                    chapterKey = chapter?.id ?: chapterNumber.toString(),
                    chapterNumber = chapterNumber,
                    chapterName = chapter?.name ?: "Chapter $chapterNumber",
                    sourceId = chapter?.let { resolvedSourceId.orEmpty() } ?: "",
                ),
            )
            onQueued()
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun loadSources() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Cache-first: if this chapter is downloaded, skip ALL network work.
            // Works fully offline because we never hit the AniList API or the
            // source parser to discover the chapter id.
            val localDownload = getCompletedChapter(mediaId, chapterNum)
            if (localDownload != null) {
                val localPages = getLocalPages(mediaId, localDownload.chapterKey)
                if (localPages.isNotEmpty()) {
                    activeParser = inferSourceName(localDownload.sourceId)
                    resolvedSourceId = localDownload.sourceId
                    resolvedChapterId = localDownload.chapterKey
                    val history = getReadingHistoryFor(mediaId)
                    val startPage = if (history != null && history.chapterNumber == chapterNum) history.lastPage else 0
                    // Best-effort media fetch — don't fail offline reads if AniList isn't reachable.
                    val mediaForStatus =
                        try {
                            getMediaDetail(mediaId)
                        } catch (_: Exception) {
                            null
                        }
                    coverUrl = mediaForStatus?.cover
                    recordActivityEvent(
                        ActivityEvent(
                            timestampMs = Clock.System.now().toEpochMilliseconds(),
                            type = "read",
                            mediaId = mediaId,
                            mediaTitle = localDownload.mangaTitle,
                            coverUrl = coverUrl,
                            chapterNumber = chapterNum,
                        ),
                    )
                    _uiState.value =
                        _uiState.value.copy(
                            title = localDownload.mangaTitle,
                            chapterTitle = localDownload.chapterName,
                            pages = localPages,
                            totalPages = localPages.size,
                            startPage = startPage.coerceIn(0, (localPages.size - 1).coerceAtLeast(0)),
                            isLoading = false,
                            mediaStatus = mediaForStatus?.status,
                            totalChapters = mediaForStatus?.totalChapters,
                        )
                    saveProgress(startPage)
                    return@launch
                }
            }

            val media = getMediaDetail(mediaId)
            val title = media?.nameRomaji ?: media?.name ?: "Unknown"
            coverUrl = media?.cover
            _uiState.value =
                _uiState.value.copy(
                    title = title,
                    chapterTitle = "Chapter $chapterNum",
                    mediaStatus = media?.status,
                    totalChapters = media?.totalChapters,
                )

            // If source ID was passed via navigation (user already picked), use it directly.
            if (navSourceId != null) {
                resolvedSourceId = navSourceId
                activeParser = inferSourceName(navSourceId)
                val history = getReadingHistoryFor(mediaId)
                val startPage = if (history != null && history.chapterNumber == chapterNum) history.lastPage else 0
                loadChapterFromSource(sourceId = navSourceId, startPage = startPage)
                return@launch
            }

            // Saved source from history — skip search.
            val history = getReadingHistoryFor(mediaId)
            if (history != null && history.sourceId.isNotEmpty()) {
                activeParser =
                    history.sourceName.ifEmpty { inferSourceName(history.sourceId) }
                resolvedSourceId = history.sourceId
                loadChapterFromSource(
                    sourceId = history.sourceId,
                    startPage = if (history.chapterNumber == chapterNum) history.lastPage else 0,
                )
                return@launch
            }

            // No saved source — search both sources and let the user pick (or
            // auto-pick when there's only one hit).
            mangaSources = resolveMangaSources(title)
            if (mangaSources.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Manga not found on any source")
                reportReaderError("Manga not found on any source", chapter = null)
                return@launch
            }

            if (mangaSources.size == 1) {
                selectSource(mangaSources.first())
            } else {
                _uiState.value =
                    _uiState.value.copy(
                        showSourceSelector = true,
                        availableSources = mangaSources,
                        isLoading = false,
                    )
            }
        }
    }

    fun selectSource(source: MangaSearchResult) {
        resolvedSourceId = source.id
        activeParser = inferSourceName(source.id)
        _uiState.value = _uiState.value.copy(showSourceSelector = false, isLoading = true)
        viewModelScope.launch {
            loadChapterFromSource(sourceId = source.id, startPage = 0)
        }
    }

    fun selectSourceById(id: String) {
        val source = mangaSources.find { it.id == id } ?: return
        selectSource(source)
    }

    fun dismissSourceSelector() {
        _uiState.value = _uiState.value.copy(showSourceSelector = false)
        mangaSources.firstOrNull()?.let { selectSource(it) }
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(error = null, isLoading = true)
        loadSources()
    }

    fun queueNextChapters(
        count: Int,
        onQueued: () -> Unit = {},
    ) {
        val state = _uiState.value
        viewModelScope.launch {
            // Banner can fire before the picker has been opened — make sure
            // we have the real chapter list (with source-side ids) before
            // queueing, so the use case can clamp + use real ids instead of
            // synthesizing fake ones past the end of the manga.
            loadChapterListNow()
            queueNextChaptersUseCase(
                mangaId = mediaId,
                mangaTitle = state.title,
                coverUrl = coverUrl,
                sourceId = resolvedSourceId.orEmpty(),
                availableChapters = _allChapters.value,
                afterChapterNumber = chapterNum,
                count = count,
            )
            onQueued()
        }
    }

    /** Bytes estimate for `count` upcoming chapters. */
    suspend fun estimateBytesForNext(count: Int): Long = estimateNextChaptersBytes(mediaId, count)

    fun formatBytes(bytes: Long): String =
        ani.saikou.domain.util
            .formatBytes(bytes)

    /**
     * Tri-state classification of what's after the current chapter, driving
     * which (if any) end-of-chapter banner the reader shows:
     *  - [Absent]    — no chapter > current exists on the source. The reader
     *                  surfaces a "you've reached the end" banner.
     *  - [Available] — there's a next chapter and it isn't on disk yet.
     *                  Lights up the "save next N" / offline banners.
     *  - [Downloaded] — the next chapter is already on disk; no banner.
     */
    enum class NextChapterStatus { Absent, Available, Downloaded }

    suspend fun nextChapterStatus(): NextChapterStatus {
        val state = _uiState.value
        val sourceMax = _allChapters.value.maxOfOrNull { it.number.toInt() } ?: 0
        // Non-airing series with a known AniList total: trust the larger of
        // (sourceMax, totalChapters). Handles fragmentary source listings —
        // MangaDex's "Vagabond (HK Colored)" hosts 5 of 327 chapters, so a
        // sourceMax-only check would falsely flag chapter 5 as the last. For
        // RELEASING / NOT_YET_RELEASED we stick with sourceMax since AniList
        // may know more chapters than have been hosted yet.
        val isAiring = state.mediaStatus == "RELEASING" || state.mediaStatus == "NOT_YET_RELEASED"
        val total = state.totalChapters
        val effectiveMax =
            if (!isAiring && total != null && total > 0) {
                maxOf(sourceMax, total)
            } else {
                sourceMax
            }
        if (chapterNum >= effectiveMax) return NextChapterStatus.Absent
        val downloaded = getCompletedChapter(mediaId, chapterNum + 1) != null
        return if (downloaded) NextChapterStatus.Downloaded else NextChapterStatus.Available
    }

    /** Called from the reader composable on page change. Debounced to avoid DB spam. */
    fun onPageChanged(page: Int) {
        saveJob?.cancel()
        saveJob =
            viewModelScope.launch {
                delay(SAVE_DEBOUNCE_MS)
                saveProgress(page)
            }
    }

    private suspend fun loadChapterFromSource(
        sourceId: String,
        startPage: Int,
    ) {
        val chapters = getChaptersForSource(sourceId)
        if (chapters.isNotEmpty()) {
            _allChapters.value = chapters
            purgePhantomDownloadsFor(chapters)
        }

        var chapter = chapters.find { it.number.toInt() == chapterNum }

        // Chapter not on the picked source? Fall back to the alternative
        // (MangaPill if we started on MangaDex, and vice versa) — covers
        // the case where MangaDex catalogs a series but doesn't host the
        // user's specific chapter. When the fallback succeeds, we also swap
        // _allChapters to MangaPill's list so the picker, "save next N",
        // and `nextChapterStatus` all reflect the source we actually use.
        if (chapter == null && activeParser == SOURCE_MANGA_DEX) {
            val title = _uiState.value.title
            val pillSources = resolveMangaSources(title).filter { inferSourceName(it.id) == SOURCE_MANGA_PILL }
            val pillFirst = pillSources.firstOrNull()
            if (pillFirst != null) {
                val pillChapters = getChaptersForSource(pillFirst.id)
                chapter = pillChapters.find { it.number.toInt() == chapterNum }
                if (chapter != null) {
                    activeParser = SOURCE_MANGA_PILL
                    resolvedSourceId = pillFirst.id
                    if (pillChapters.isNotEmpty()) {
                        _allChapters.value = pillChapters
                        purgePhantomDownloadsFor(pillChapters)
                    }
                }
            }
        }

        if (chapter == null) {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = false,
                    error = "Chapter $chapterNum not found on any source",
                )
            reportReaderError("Chapter not found on any source", chapter = chapterNum)
            return
        }

        resolvedChapterId = chapter.id
        _uiState.value = _uiState.value.copy(chapterTitle = chapter.name)

        val pages = getChapterPages(chapter.id)
        if (pages.isEmpty()) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "No pages found for chapter $chapterNum")
            reportReaderError("No pages found for chapter", chapter = chapterNum)
            return
        }

        _uiState.value =
            _uiState.value.copy(
                pages = pages,
                totalPages = pages.size,
                startPage = startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                isLoading = false,
            )

        recordActivityEvent(
            ActivityEvent(
                timestampMs = Clock.System.now().toEpochMilliseconds(),
                type = "read",
                mediaId = mediaId,
                mediaTitle = _uiState.value.title,
                coverUrl = coverUrl,
                chapterNumber = chapterNum,
            ),
        )

        saveProgress(startPage)
    }

    private suspend fun saveProgress(page: Int) {
        val state = _uiState.value
        val srcId = resolvedSourceId ?: return
        val chapId = resolvedChapterId ?: return

        upsertReadingHistory(
            ReadingHistoryItem(
                mangaId = mediaId,
                mangaTitle = state.title,
                coverUrl = coverUrl,
                chapterNumber = chapterNum,
                chapterName = state.chapterTitle,
                chapterId = chapId,
                sourceId = srcId,
                sourceName = activeParser,
                lastPage = page.coerceAtLeast(0),
                totalPages = state.totalPages,
                lastReadAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )

        val totalPages = state.totalPages
        if (totalPages > 0 && !anilistProgressSynced) {
            val readFraction = (page + 1).toFloat() / totalPages
            if (readFraction >= ANILIST_PROGRESS_FRACTION) {
                anilistProgressSynced = true
                try {
                    editListEntry(mediaId = mediaId, progress = chapterNum, status = "CURRENT")
                    ListEventBus.emit(ListEvent.ReadingProgressUpdated(mediaId, chapterNum))
                } catch (e: Exception) {
                    // Don't latch — let the next page tick retry.
                    logger.reportError(
                        area = "MangaReader",
                        method = "anilistSync",
                        throwable = e,
                        extras = mapOf("mediaId" to mediaId.toString(), "chapter" to chapterNum.toString()),
                    )
                    anilistProgressSynced = false
                }
            }
        }
    }

    override fun onCleared() {
        saveJob?.cancel()
        super.onCleared()
    }

    private fun reportReaderError(
        message: String,
        chapter: Int?,
    ) {
        val extras =
            buildMap {
                put("mediaId", mediaId.toString())
                if (chapter != null) put("chapter", chapter.toString())
                put("activeParser", activeParser)
                put("title", _uiState.value.title)
                put("resolvedSourceId", resolvedSourceId ?: "")
            }
        logger.reportWarning(
            area = "MangaReader",
            method = "loadSources",
            message = message,
            extras = extras,
        )
    }

    /**
     * MangaPill source ids are path-shaped (`/manga/...` or `/chapters/...`),
     * MangaDex ids are UUIDs. The repo dispatches by the same rule; this
     * helper just maps an id to a display name for history rows.
     */
    private fun inferSourceName(id: String): String =
        if (id.startsWith("/manga/") || id.startsWith("/chapters/")) SOURCE_MANGA_PILL else SOURCE_MANGA_DEX

    companion object {
        private const val SOURCE_MANGA_DEX = "MangaDex"
        private const val SOURCE_MANGA_PILL = "MangaPill"
        private const val ANILIST_PROGRESS_FRACTION = 0.80f
        private const val SAVE_DEBOUNCE_MS = 2_000L
    }
}

data class ReaderUiState(
    val title: String = "",
    val chapterTitle: String = "",
    val pages: List<MangaPage> = emptyList(),
    val totalPages: Int = 0,
    val startPage: Int = 0,
    /** Sources the user can pick from when no id was passed via navigation. */
    val availableSources: List<MangaSearchResult> = emptyList(),
    val showSourceSelector: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Opaque id of the manga on the resolved source — forwarded to the next chapter so it doesn't re-search. */
    val resolvedSourceId: String? = null,
    /** AniList publication status — RELEASING / FINISHED / HIATUS / CANCELLED / NOT_YET_RELEASED. Drives the
     *  end-of-series banner copy on the last chapter (FINISHED → "complete", else → "caught up"). */
    val mediaStatus: String? = null,
    /** AniList's known total chapter count. Used as a sanity check by `nextChapterStatus` for non-airing
     *  series — when the user's picked source is a fragmentary edition (e.g. MangaDex's "Vagabond
     *  (HK Colored)" hosts only 5 of 327 chapters), the source list under-reports the real series length
     *  and would falsely flag every chapter past the partial cap as the "last chapter". Null for series
     *  where AniList doesn't surface a count (typical for airing manga). */
    val totalChapters: Int? = null,
)
