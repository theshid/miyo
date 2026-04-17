package ani.saikou.screens.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.components.SourceItem
import ani.saikou.data.local.db.ReadingHistoryEntity
import ani.saikou.data.remote.parsers.MangaDexParser
import ani.saikou.di.AppModule
import ani.saikou.data.local.ListEvent
import ani.saikou.data.local.ListEventBus
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSource
import ani.saikou.logging.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MangaReaderViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository = AppModule.repository()
    private val historyDao = AppModule.readingHistoryDao()
    private val mangaDex = MangaDexParser()

    val mediaId: Int = savedStateHandle["mediaId"] ?: 0
    val chapterNum: Int = savedStateHandle["chapterNum"] ?: 1

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState

    private var mangaSources: List<MangaSource> = emptyList()
    private var resolvedSourceId: String? = null
    private var resolvedChapterId: String? = null
    private var coverUrl: String? = null
    private var saveJob: Job? = null
    private var anilistProgressSynced = false

    init {
        loadSources()
    }

    private fun loadSources() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val media = repository.getMedia(mediaId)
            val title = media?.nameRomaji ?: media?.name ?: "Unknown"
            coverUrl = media?.cover
            _uiState.value = _uiState.value.copy(title = title, chapterTitle = "Chapter $chapterNum")

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

            // No history — search MangaDex
            mangaSources = mangaDex.search(title)
            if (mangaSources.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Manga not found on MangaDex")
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
        val chapters = mangaDex.getChapters(sourceId)
        val chapter = chapters.find { it.number.toInt() == chapterNum }
        if (chapter == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "Chapter $chapterNum not found")
            return
        }

        resolvedChapterId = chapter.id
        _uiState.value = _uiState.value.copy(chapterTitle = chapter.name)

        val pages = mangaDex.getPages(chapter.id)
        _uiState.value = _uiState.value.copy(
            pages = pages,
            totalPages = pages.size,
            startPage = startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
            isLoading = false,
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
                sourceName = "MangaDex",
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
