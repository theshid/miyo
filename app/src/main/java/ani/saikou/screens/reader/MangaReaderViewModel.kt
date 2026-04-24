package ani.saikou.screens.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.components.ChapterDownloadState
import ani.saikou.components.SourceItem
import ani.saikou.data.local.db.ActivityEventEntity
import ani.saikou.domain.model.Chapter
import ani.saikou.data.local.db.ReadingHistoryEntity
import ani.saikou.data.remote.parsers.MangaDexParser
import ani.saikou.data.remote.parsers.MangaPillParser
import ani.saikou.di.AppModule
import ani.saikou.data.local.ListEvent
import ani.saikou.data.local.ListEventBus
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSource
import io.github.theshid.prettylog.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MangaReaderViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository = AppModule.repository()
    private val historyDao = AppModule.readingHistoryDao()
    private val activityDao = AppModule.activityEventDao()
    private val downloadDao = AppModule.downloadDao()
    private val downloadManager = AppModule.downloadManager()
    private val sizeEstimator = ani.saikou.data.local.downloads.ChapterSizeEstimator(downloadDao)
    private val mangaDex = MangaDexParser()
    private val mangaPill = MangaPillParser()
    private var activeParser: String = "MangaDex" // tracks which parser resolved the source

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
    val chapterDownloads: StateFlow<Map<Int, ChapterDownloadState>> = downloadDao
        .getDownloadsForManga(mediaId)
        .map { rows ->
            rows.filter { it.chapterNumber >= 0 }.associate { row ->
                row.chapterNumber to ChapterDownloadState(
                    status = row.status,
                    downloadedPages = row.downloadedPages,
                    totalPages = row.totalPages,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var mangaSources: List<MangaSource> = emptyList()
    private var resolvedSourceId: String? = null
    private var resolvedChapterId: String? = null
    private var coverUrl: String? = null
    private var saveJob: Job? = null
    private var anilistProgressSynced = false

    init {
        loadSources()
    }

    /**
     * Populate [allChapters] for the chapter picker. If we already have a
     * resolved source id, hit its parser directly. Otherwise (common when the
     * user opened a cached/offline chapter and never hit the live source),
     * fall back to a title search so the sheet still gets a list. No-op if
     * the list is already loaded. Safe to call from UI — idempotent.
     */
    fun ensureChapterListLoaded() {
        if (_allChapters.value.isNotEmpty() || _chapterListLoading.value) return
        viewModelScope.launch {
            _chapterListLoading.value = true
            val chapters = try {
                val sid = resolvedSourceId?.takeIf { it.isNotEmpty() }
                if (sid != null) {
                    if (activeParser == "MangaPill") mangaPill.getChapters(sid)
                    else mangaDex.getChapters(sid)
                } else {
                    // Use MangaDex's own `lastChapter` hint to detect licensed
                    // titles it only partially hosts. If the gap between
                    // "series has 220 chapters" and "feed returned 7" is big,
                    // we know MangaDex isn't the right source and skip straight
                    // to MangaPill without a wasted getChapters round trip.
                    val title = _uiState.value.title
                    val mdx = mangaDex.search(title).firstOrNull()
                    val mdxChapters = if (mdx != null) {
                        runCatching { mangaDex.getChapters(mdx.id) }.getOrDefault(emptyList())
                    } else emptyList()
                    val mdxCovers = mdx?.totalChapterHint == null ||
                        mdxChapters.size >= (mdx.totalChapterHint * 0.9)
                    if (mdxChapters.isNotEmpty() && mdxCovers) {
                        activeParser = "MangaDex"
                        resolvedSourceId = mdx!!.id
                        mdxChapters
                    } else {
                        val mp = mangaPill.search(title).firstOrNull()
                        val mpChapters = if (mp != null) {
                            runCatching { mangaPill.getChapters(mp.id) }.getOrDefault(emptyList())
                        } else emptyList()
                        // Last resort: fall back to MangaDex's partial list if
                        // MangaPill had nothing either.
                        when {
                            mpChapters.size > mdxChapters.size -> {
                                activeParser = "MangaPill"
                                resolvedSourceId = mp!!.id
                                mpChapters
                            }
                            mdxChapters.isNotEmpty() -> {
                                activeParser = "MangaDex"
                                resolvedSourceId = mdx!!.id
                                mdxChapters
                            }
                            else -> emptyList()
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
            _allChapters.value = chapters
            _chapterListLoading.value = false
        }
    }

    fun cancelDownload(chapterNumber: Int) {
        viewModelScope.launch {
            downloadManager.cancelDownload("${mediaId}_$chapterNumber")
        }
    }

    fun queueSingleChapterDownload(chapterNumber: Int, onQueued: () -> Unit = {}) {
        val state = _uiState.value
        viewModelScope.launch {
            val id = "${mediaId}_$chapterNumber"
            val existing = downloadDao.getDownload(id)
            if (existing?.status == "COMPLETED" || existing?.status == "DOWNLOADING" || existing?.status == "QUEUED") {
                return@launch
            }
            downloadManager.queueDownload(
                mangaId = mediaId,
                mangaTitle = state.title,
                coverUrl = coverUrl,
                chapterKey = chapterNumber.toString(),
                chapterNumber = chapterNumber,
                chapterName = "Chapter $chapterNumber",
                sourceId = "",
                totalPages = 0,
            )
            onQueued()
        }
    }

    private fun loadSources() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Cache-first: if this chapter is downloaded, skip ALL network work.
            // Works even fully offline because we don't touch the AniList API
            // or the source parser to discover the chapter id.
            val localDownload = downloadDao.getCompletedByChapterNumber(mediaId, chapterNum)
            if (localDownload != null) {
                val localPages = downloadManager.getLocalPages(mediaId, localDownload.chapterKey)
                if (!localPages.isNullOrEmpty()) {
                    activeParser = localDownload.sourceId.let { sid ->
                        if (sid.startsWith("/manga/") || sid.startsWith("/chapters/")) "MangaPill" else "MangaDex"
                    }
                    resolvedSourceId = localDownload.sourceId
                    resolvedChapterId = localDownload.chapterKey
                    val history = historyDao.getForManga(mediaId)
                    val startPage = if (history != null && history.chapterNumber == chapterNum) history.lastPage else 0
                    // Best-effort cover fetch — don't fail offline reads if AniList isn't reachable.
                    coverUrl = try { repository.getMedia(mediaId)?.cover } catch (_: Exception) { null }
                    Log.d(tag = "MangaReader", message = "Loaded ${localPages.size} pages from local cache for chapter $chapterNum")
                    activityDao.insert(
                        ActivityEventEntity(timestampMs = System.currentTimeMillis(), type = "read")
                    )
                    _uiState.value = _uiState.value.copy(
                        title = localDownload.mangaTitle,
                        chapterTitle = localDownload.chapterName,
                        pages = localPages,
                        totalPages = localPages.size,
                        startPage = startPage.coerceIn(0, (localPages.size - 1).coerceAtLeast(0)),
                        isLoading = false,
                    )
                    saveProgress(startPage)
                    return@launch
                }
            }

            val media = repository.getMedia(mediaId)
            val title = media?.nameRomaji ?: media?.name ?: "Unknown"
            coverUrl = media?.cover
            _uiState.value = _uiState.value.copy(title = title, chapterTitle = "Chapter $chapterNum")

            // If source ID was passed via navigation (user already picked), use it directly
            if (navSourceId != null) {
                resolvedSourceId = navSourceId
                val history = historyDao.getForManga(mediaId)
                val startPage = if (history != null && history.chapterNumber == chapterNum) history.lastPage else 0
                loadChapterFromSource(sourceId = navSourceId, startPage = startPage)
                return@launch
            }

            // Check if we have a saved source from history — skip search
            val history = historyDao.getForManga(mediaId)
            if (history != null && history.sourceId.isNotEmpty()) {
                resolvedSourceId = history.sourceId
                loadChapterFromSource(
                    sourceId = history.sourceId,
                    startPage = if (history.chapterNumber == chapterNum) history.lastPage else 0,
                )
                return@launch
            }

            // Search MangaDex first, then fall back to MangaPill
            mangaSources = mangaDex.search(title)
            if (mangaSources.isNotEmpty()) {
                activeParser = "MangaDex"
                Log.d(tag = "MangaReader", message = "Found ${mangaSources.size} sources on MangaDex")
            } else {
                // Fallback: try MangaPill (has licensed manga that MangaDex doesn't)
                Log.d(tag = "MangaReader", message = "No results on MangaDex — trying MangaPill")
                mangaSources = mangaPill.search(title)
                activeParser = "MangaPill"
                Log.d(tag = "MangaReader", message = "Found ${mangaSources.size} sources on MangaPill")
            }

            if (mangaSources.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Manga not found on any source")
                return@launch
            }

            if (mangaSources.size == 1) {
                selectSource(mangaSources.first())
            } else {
                _uiState.value = _uiState.value.copy(
                    showSourceSelector = true,
                    availableSources = mangaSources.map {
                        SourceItem(id = it.id, title = it.title, coverUrl = it.coverUrl)
                    },
                    isLoading = false,
                )
            }
        }
    }

    fun selectSource(source: MangaSource) {
        resolvedSourceId = source.id
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

    /**
     * Queue chapters [chapterNum + 1 .. chapterNum + count] for download.
     * Relies on the same MangaDex→MangaPill fallback the reader uses.
     */
    fun queueNextChapters(count: Int, onQueued: () -> Unit = {}) {
        val state = _uiState.value
        viewModelScope.launch {
            for (offset in 1..count) {
                val nextChapter = chapterNum + offset
                val id = "${mediaId}_$nextChapter"
                val existing = downloadDao.getDownload(id)
                if (existing?.status == "COMPLETED" || existing?.status == "DOWNLOADING" || existing?.status == "QUEUED") {
                    continue // already have it (or working on it)
                }
                downloadManager.queueDownload(
                    mangaId = mediaId,
                    mangaTitle = state.title,
                    coverUrl = coverUrl,
                    chapterKey = nextChapter.toString(),
                    chapterNumber = nextChapter,
                    chapterName = "Chapter $nextChapter",
                    sourceId = "",
                    totalPages = 0,
                )
            }
            onQueued()
        }
    }

    /** Bytes estimate for `count` upcoming chapters, rendered via [ChapterSizeEstimator.format]. */
    suspend fun estimateBytesForNext(count: Int): Long =
        sizeEstimator.estimateBytes(mediaId, count)

    fun formatBytes(bytes: Long): String = sizeEstimator.format(bytes)

    /** True iff the next chapter is NOT yet fully downloaded on this device. */
    suspend fun isNextChapterMissing(): Boolean {
        val nextChapter = chapterNum + 1
        val existing = downloadDao.getCompletedByChapterNumber(mediaId, nextChapter)
        return existing == null
    }

    /**
     * Called from the reader composable whenever the page changes.
     * Debounced to avoid spamming the DB on every scroll frame.
     */
    fun onPageChanged(page: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(2000) // Debounce 2 seconds
            saveProgress(page)
        }
    }

    private suspend fun loadChapterFromSource(sourceId: String, startPage: Int) {
        // Use the parser that found this source
        val isMangaPill = activeParser == "MangaPill" || sourceId.startsWith("/manga/") || sourceId.startsWith("/chapters/")
        val parserName = if (isMangaPill) "MangaPill" else "MangaDex"

        Log.d(tag = "MangaReader", message = "loadChapter: sourceId=$sourceId, parser=$parserName, chapter=$chapterNum")

        val chapters = if (isMangaPill) mangaPill.getChapters(sourceId) else mangaDex.getChapters(sourceId)
        if (chapters.isNotEmpty()) _allChapters.value = chapters
        Log.d(tag = "MangaReader", message = "Found ${chapters.size} chapters on $parserName")
        if (chapters.isNotEmpty()) {
            Log.d(tag = "MangaReader", message = "First: ${chapters.first().number}, Last: ${chapters.last().number}")
        }

        var chapter = chapters.find { it.number.toInt() == chapterNum }
        var usedParser = parserName
        var usedMangaPill = isMangaPill

        // If chapter not found on primary parser, try the fallback
        if (chapter == null && !isMangaPill) {
            Log.d(tag = "MangaReader", message = "Chapter $chapterNum not on $parserName — trying MangaPill fallback")
            val title = _uiState.value.title
            val pillSources = mangaPill.search(title)
            if (pillSources.isNotEmpty()) {
                val pillChapters = mangaPill.getChapters(pillSources.first().id)
                Log.d(tag = "MangaReader", message = "MangaPill: ${pillChapters.size} chapters found")
                chapter = pillChapters.find { it.number.toInt() == chapterNum }
                if (chapter != null) {
                    usedParser = "MangaPill"
                    usedMangaPill = true
                    activeParser = "MangaPill"
                    resolvedSourceId = pillSources.first().id
                }
            }
        }

        if (chapter == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Chapter $chapterNum not found on any source",
            )
            return
        }

        Log.d(tag = "MangaReader", message = "Loading pages for: ${chapter.name}, id=${chapter.id} via $usedParser")

        resolvedChapterId = chapter.id
        _uiState.value = _uiState.value.copy(chapterTitle = chapter.name)

        val pages = if (usedMangaPill) mangaPill.getPages(chapter.id) else mangaDex.getPages(chapter.id)
        Log.d(tag = "MangaReader", message = "Got ${pages.size} pages")

        if (pages.isEmpty()) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "No pages found for chapter $chapterNum")
            return
        }

        _uiState.value = _uiState.value.copy(
            pages = pages,
            totalPages = pages.size,
            startPage = startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
            isLoading = false,
        )

        activityDao.insert(
            ActivityEventEntity(timestampMs = System.currentTimeMillis(), type = "read")
        )

        // Save initial history entry
        saveProgress(startPage)
    }

    private suspend fun saveProgress(page: Int) {
        val state = _uiState.value
        val srcId = resolvedSourceId ?: return
        val chapId = resolvedChapterId ?: return

        historyDao.upsert(
            ReadingHistoryEntity(
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
                lastReadAt = System.currentTimeMillis(),
            )
        )

        // Sync progress to AniList when ≥80% of the chapter is read (once per chapter)
        val totalPages = state.totalPages
        if (totalPages > 0 && !anilistProgressSynced) {
            val readFraction = (page + 1).toFloat() / totalPages
            if (readFraction >= 0.80f) {
                anilistProgressSynced = true
                try {
                    repository.editListEntry(
                        mediaId = mediaId,
                        progress = chapterNum,
                        status = "CURRENT",
                    )
                    Log.i(tag = "AniSync", message = "Synced reading progress to AniList: $mediaId ch $chapterNum")
                    ListEventBus.emit(ListEvent.ReadingProgressUpdated(mediaId, chapterNum))
                } catch (e: Exception) {
                    Log.e(tag = "AniSync", message = "Failed to sync reading progress", throwable = e)
                    anilistProgressSynced = false
                }
            }
        }
    }

    override fun onCleared() {
        // Final save when leaving reader
        saveJob?.cancel()
        // Can't use suspend here, but the debounced save should have caught the last page
        super.onCleared()
    }
}

data class ReaderUiState(
    val title: String = "",
    val chapterTitle: String = "",
    val pages: List<MangaPage> = emptyList(),
    val totalPages: Int = 0,
    val startPage: Int = 0,
    val availableSources: List<SourceItem> = emptyList(),
    val showSourceSelector: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)
