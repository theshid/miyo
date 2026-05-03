package ani.saikou.presentation.screens.downloads

import ani.saikou.domain.model.Download
import ani.saikou.domain.model.DownloadStatus
import ani.saikou.domain.model.DownloadedManga
import ani.saikou.domain.model.DownloadsSnapshot
import ani.saikou.domain.model.MangaWithDownloads
import ani.saikou.domain.usecase.downloads.CancelChapterDownloadUseCase
import ani.saikou.domain.usecase.downloads.DeleteAllDownloadsForMangaUseCase
import ani.saikou.domain.usecase.downloads.EvictReadChaptersUseCase
import ani.saikou.domain.usecase.downloads.ObserveDownloadsUseCase
import ani.saikou.domain.usecase.downloads.PauseChapterDownloadUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the [DownloadsViewModel] state-machine after the use-case
 * migration: the initial loading flag, the snapshot-driven state
 * collection, the evictable-summary roll-up, and the four mutation
 * actions that delegate to their respective use cases.
 *
 * VM no longer touches the repository — every collaborator is a use
 * case. The grouping/filter logic now lives in ObserveDownloadsUseCase,
 * so those tests moved with it; the tests here verify the VM's
 * snapshot-to-state mapping, not the orchestration upstream of it.
 *
 * Uses StandardTestDispatcher + runCurrent — every state we assert is
 * reachable as `state.value` after the dispatcher drains.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val observeDownloads: ObserveDownloadsUseCase = mockk()
    private val cancelChapter: CancelChapterDownloadUseCase = mockk(relaxUnitFun = true)
    private val deleteMangaDownloads: DeleteAllDownloadsForMangaUseCase = mockk(relaxUnitFun = true)

    // relaxed (not relaxUnitFun) — invoke() returns EvictionSummary, not Unit.
    private val evictReadChapters: EvictReadChaptersUseCase = mockk(relaxed = true)
    private val pauseChapter: PauseChapterDownloadUseCase = mockk(relaxUnitFun = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading with empty list before snapshot emits`() =
        testScope.runTest {
            // No emissions ever — the VM never updates state.
            every { observeDownloads() } returns emptyFlow()

            val vm = newVm()

            val initial = vm.uiState.value
            assertTrue(initial.isLoading)
            assertTrue(initial.mangaList.isEmpty())
        }

    @Test
    fun `snapshot drives the state and clears the loading flag`() =
        testScope.runTest {
            val mangaA = manga(id = 42, title = "Vagabond")
            val ch1 = download(id = "42_1", mangaId = 42, chapterNumber = 1)
            val ch2 = download(id = "42_2", mangaId = 42, chapterNumber = 2)
            every { observeDownloads() } returns
                flowOf(
                    DownloadsSnapshot(
                        mangaWithDownloads = listOf(MangaWithDownloads(manga = mangaA, chapters = listOf(ch1, ch2))),
                        totalStorageUsed = 5_000L,
                        freeSpace = 10_000L,
                        readChapters = emptyList(),
                    ),
                )

            val vm = newVm()
            runCurrent()

            val state = vm.uiState.value
            assertEquals(1, state.mangaList.size)
            assertEquals(42, state.mangaList[0].manga.mangaId)
            assertEquals(listOf("42_1", "42_2"), state.mangaList[0].chapters.map { it.id })
            assertEquals(5_000L, state.totalStorageUsed)
            assertEquals(10_000L, state.freeSpace)
            assertFalse(state.isLoading)
        }

    @Test
    fun `evictable summary count and bytes are rolled up into state`() =
        testScope.runTest {
            every { observeDownloads() } returns
                flowOf(
                    DownloadsSnapshot(
                        mangaWithDownloads = emptyList(),
                        totalStorageUsed = 0L,
                        freeSpace = 0L,
                        readChapters =
                            listOf(
                                download(id = "42_1", mangaId = 42, chapterNumber = 1, fileSizeBytes = 100L),
                                download(id = "42_2", mangaId = 42, chapterNumber = 2, fileSizeBytes = 250L),
                            ),
                    ),
                )

            val vm = newVm()
            runCurrent()

            val state = vm.uiState.value
            assertEquals(2, state.readChapterCount)
            assertEquals(350L, state.readChapterBytes)
        }

    @Test
    fun `clearReadChapters delegates to the EvictReadChapters use case`() =
        testScope.runTest {
            every { observeDownloads() } returns emptyFlow()
            val vm = newVm()

            vm.clearReadChapters()
            runCurrent()

            coVerify { evictReadChapters() }
        }

    @Test
    fun `deleteChapter delegates to CancelChapterDownload with the given id`() =
        testScope.runTest {
            every { observeDownloads() } returns emptyFlow()
            val vm = newVm()

            vm.deleteChapter("42_5")
            runCurrent()

            coVerify { cancelChapter("42_5") }
        }

    @Test
    fun `deleteAllForManga delegates to DeleteAllDownloadsForManga with the given id`() =
        testScope.runTest {
            every { observeDownloads() } returns emptyFlow()
            val vm = newVm()

            vm.deleteAllForManga(42)
            runCurrent()

            coVerify { deleteMangaDownloads(42) }
        }

    @Test
    fun `pauseDownload delegates to PauseChapterDownload with the given id`() =
        testScope.runTest {
            every { observeDownloads() } returns emptyFlow()
            val vm = newVm()

            vm.pauseDownload("42_5")
            runCurrent()

            coVerify { pauseChapter("42_5") }
        }

    // -------- helpers --------

    private fun newVm() =
        DownloadsViewModel(
            observeDownloads = observeDownloads,
            cancelChapter = cancelChapter,
            deleteMangaDownloads = deleteMangaDownloads,
            evictReadChapters = evictReadChapters,
            pauseChapter = pauseChapter,
        )

    private fun manga(
        id: Int,
        title: String,
    ) = DownloadedManga(mangaId = id, title = title, coverUrl = null, sourceId = "MangaDex")

    private fun download(
        id: String,
        mangaId: Int,
        chapterNumber: Int = 1,
        fileSizeBytes: Long = 0L,
    ) = Download(
        id = id,
        mangaId = mangaId,
        mangaTitle = "Vagabond",
        chapterKey = id.substringAfterLast('_'),
        chapterNumber = chapterNumber,
        chapterName = "Chapter $chapterNumber",
        sourceId = "MangaDex",
        status = DownloadStatus.COMPLETED,
        totalPages = 0,
        downloadedPages = 0,
        fileSizeBytes = fileSizeBytes,
        createdAt = 0L,
    )
}
