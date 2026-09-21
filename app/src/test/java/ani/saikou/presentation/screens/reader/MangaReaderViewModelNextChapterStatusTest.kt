package ani.saikou.presentation.screens.reader

import androidx.lifecycle.SavedStateHandle
import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.Download
import ani.saikou.domain.model.DownloadStatus
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.usecase.activity.RecordActivityEventUseCase
import ani.saikou.domain.usecase.anilist.EditListEntryUseCase
import ani.saikou.domain.usecase.anilist.GetMediaDetailUseCase
import ani.saikou.domain.usecase.anilist.ResolveChapterCountUseCase
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Pins [MangaReaderViewModel.nextChapterStatus] — specifically that
 * "you've reached the end of the series" is only ever claimed off a
 * *positive* signal.
 *
 * The regression this guards: reading a downloaded chapter with no network,
 * every lookup that could prove more chapters exist fails (AniList detail →
 * null totalChapters, source chapter list → empty), and the old
 * `chapterNum >= effectiveMax` fall-through resolved that to Absent — the
 * reader told an offline user they'd finished the manga at whatever chapter
 * they'd stopped on. It must resolve to Unknown instead.
 *
 * Every test drives the fully-offline cache-first path in `loadSources()`,
 * since that's the only way to reach the reader without a network.
 *
 * Uses StandardTestDispatcher + runCurrent — nextChapterStatus() is a plain
 * suspend call, so the state we assert is its return value directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MangaReaderViewModelNextChapterStatusTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    private val getMediaDetail: GetMediaDetailUseCase = mockk()
    private val getReadingHistoryFor: GetReadingHistoryForMediaUseCase = mockk()
    private val getChaptersForSource: GetChaptersForSourceUseCase = mockk()
    private val getCompletedChapter: GetCompletedChapterUseCase = mockk()
    private val getLocalPages: GetLocalPagesUseCase = mockk()
    private val resolveChapterCount: ResolveChapterCountUseCase = mockk()
    private val observeChapterDownloads: ObserveChapterDownloadsForMangaUseCase = mockk()

    private val editListEntry: EditListEntryUseCase = mockk(relaxed = true)
    private val upsertReadingHistory: UpsertReadingHistoryUseCase = mockk(relaxed = true)
    private val recordActivityEvent: RecordActivityEventUseCase = mockk(relaxed = true)
    private val resolveMangaSources: ResolveMangaSourcesUseCase = mockk(relaxed = true)
    private val resolveChaptersForManga: ResolveChaptersForMangaUseCase = mockk(relaxed = true)
    private val getChapterPages: GetChapterPagesUseCase = mockk(relaxed = true)
    private val estimateNextChaptersBytes: EstimateNextChaptersBytesUseCase = mockk(relaxed = true)
    private val queueChapter: QueueChapterDownloadUseCase = mockk(relaxed = true)
    private val queueNextChapters: QueueNextChaptersUseCase = mockk(relaxed = true)
    private val cancelChapter: CancelChapterDownloadUseCase = mockk(relaxed = true)
    private val cancelChapterByNumber: CancelChapterByNumberUseCase = mockk(relaxed = true)
    private val cleanupPhantomDownloads: CleanupPhantomDownloadsUseCase = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { observeChapterDownloads(MANGA_ID) } returns flowOf(emptyMap())
        coEvery { getCompletedChapter(MANGA_ID, CURRENT_CHAPTER) } returns downloadedChapter()
        coEvery { getLocalPages(MANGA_ID, CHAPTER_KEY) } returns
            listOf(MangaPage(index = 0, imageUrl = "file:///page0.jpg"))
        coEvery { getReadingHistoryFor(MANGA_ID) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `offline with no chapter list and no known total reports Unknown, not Absent`() =
        testScope.runTest {
            // Offline: AniList detail and the source chapter list both fail, so
            // nothing can prove a chapter 6 exists — and nothing disproves it.
            coEvery { getMediaDetail(MANGA_ID) } throws IOException("offline")
            coEvery { resolveChapterCount(any(), any()) } throws IOException("offline")
            coEvery { getChaptersForSource(SOURCE_ID) } throws IOException("offline")

            val vm = newVm()
            runCurrent()

            assertEquals(null, vm.uiState.value.totalChapters)
            assertEquals(MangaReaderViewModel.NextChapterStatus.Unknown, vm.nextChapterStatus())
        }

    @Test
    fun `offline but AniList total is cached still reports Available below the total`() =
        testScope.runTest {
            // totalChapters survives from the cached media detail, so we *do*
            // have evidence chapter 6 exists even though the source is down.
            coEvery { getMediaDetail(MANGA_ID) } returns media(totalChapters = 10)
            coEvery { resolveChapterCount(any(), any()) } throws IOException("offline")
            coEvery { getChaptersForSource(SOURCE_ID) } throws IOException("offline")
            coEvery { getCompletedChapter(MANGA_ID, CURRENT_CHAPTER + 1) } returns null

            val vm = newVm()
            runCurrent()

            assertEquals(MangaReaderViewModel.NextChapterStatus.Available, vm.nextChapterStatus())
        }

    @Test
    fun `a real last chapter still reports Absent`() =
        testScope.runTest {
            // The positive signal: the source listed chapters and none is > 5.
            coEvery { getMediaDetail(MANGA_ID) } throws IOException("offline")
            coEvery { resolveChapterCount(any(), any()) } throws IOException("offline")
            coEvery { getChaptersForSource(SOURCE_ID) } returns chapters(1..CURRENT_CHAPTER)

            val vm = newVm()
            runCurrent()

            assertEquals(MangaReaderViewModel.NextChapterStatus.Absent, vm.nextChapterStatus())
        }

    @Test
    fun `a mid-series chapter with the list loaded reports Available`() =
        testScope.runTest {
            coEvery { getMediaDetail(MANGA_ID) } throws IOException("offline")
            coEvery { resolveChapterCount(any(), any()) } throws IOException("offline")
            coEvery { getChaptersForSource(SOURCE_ID) } returns chapters(1..10)
            coEvery { getCompletedChapter(MANGA_ID, CURRENT_CHAPTER + 1) } returns null

            val vm = newVm()
            runCurrent()

            assertEquals(MangaReaderViewModel.NextChapterStatus.Available, vm.nextChapterStatus())
        }

    @Test
    fun `an already-downloaded next chapter reports Downloaded`() =
        testScope.runTest {
            coEvery { getMediaDetail(MANGA_ID) } throws IOException("offline")
            coEvery { resolveChapterCount(any(), any()) } throws IOException("offline")
            coEvery { getChaptersForSource(SOURCE_ID) } returns chapters(1..10)
            coEvery { getCompletedChapter(MANGA_ID, CURRENT_CHAPTER + 1) } returns
                downloadedChapter(number = CURRENT_CHAPTER + 1)

            val vm = newVm()
            runCurrent()

            assertEquals(MangaReaderViewModel.NextChapterStatus.Downloaded, vm.nextChapterStatus())
        }

    private fun newVm() =
        MangaReaderViewModel(
            savedStateHandle =
                SavedStateHandle(
                    mapOf("mediaId" to MANGA_ID, "chapterNum" to CURRENT_CHAPTER),
                ),
            getMediaDetail = getMediaDetail,
            editListEntry = editListEntry,
            getReadingHistoryFor = getReadingHistoryFor,
            upsertReadingHistory = upsertReadingHistory,
            recordActivityEvent = recordActivityEvent,
            resolveMangaSources = resolveMangaSources,
            resolveChaptersForManga = resolveChaptersForManga,
            resolveChapterCount = resolveChapterCount,
            getChaptersForSource = getChaptersForSource,
            getChapterPages = getChapterPages,
            getCompletedChapter = getCompletedChapter,
            getLocalPages = getLocalPages,
            estimateNextChaptersBytes = estimateNextChaptersBytes,
            queueChapter = queueChapter,
            queueNextChaptersUseCase = queueNextChapters,
            cancelChapter = cancelChapter,
            cancelChapterByNumber = cancelChapterByNumber,
            cleanupPhantomDownloads = cleanupPhantomDownloads,
            observeChapterDownloads = observeChapterDownloads,
            logger = logger,
        )

    private fun media(totalChapters: Int?) =
        ani.saikou.domain.model.Media(
            id = MANGA_ID,
            name = "Test Manga",
            nameRomaji = "Test Manga",
            totalChapters = totalChapters,
        )

    private fun chapters(range: IntRange) = range.map { Chapter(id = "ch-$it", number = it.toFloat(), name = "Chapter $it") }

    private fun downloadedChapter(number: Int = CURRENT_CHAPTER) =
        Download(
            id = "${MANGA_ID}_$number",
            mangaId = MANGA_ID,
            mangaTitle = "Test Manga",
            chapterKey = if (number == CURRENT_CHAPTER) CHAPTER_KEY else "key-$number",
            chapterNumber = number,
            chapterName = "Chapter $number",
            sourceId = SOURCE_ID,
            status = DownloadStatus.COMPLETED,
            totalPages = 1,
            downloadedPages = 1,
            fileSizeBytes = 1_000L,
            createdAt = 0L,
        )

    private companion object {
        const val MANGA_ID = 42
        const val CURRENT_CHAPTER = 5
        const val CHAPTER_KEY = "key-5"
        const val SOURCE_ID = "mangadex:abc"
    }
}
