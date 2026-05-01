package ani.saikou.screens.downloads

import ani.saikou.domain.model.Download
import ani.saikou.domain.model.DownloadStatus
import ani.saikou.domain.model.DownloadedManga
import ani.saikou.domain.model.EvictionSummary
import ani.saikou.domain.repository.DownloadRepository
import io.mockk.coEvery
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
 * Pins the [DownloadsViewModel] state-machine: the initial loading flag,
 * the combine-and-group transform that buckets downloads under their
 * parent manga, the empty-manga filter, the evictable-summary roll-up,
 * and the four mutation actions that delegate to the repo.
 *
 * Uses the StandardTestDispatcher + runCurrent pattern (no Turbine —
 * every state we assert is reachable as `state.value` after the
 * dispatcher drains; there are no SharedFlow event channels to collect).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val repo: DownloadRepository = mockk(relaxUnitFun = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // The combine transform calls these every emission — give them
        // safe defaults so individual tests only stub what they care about.
        coEvery { repo.storageUsedBytes() } returns 0L
        coEvery { repo.availableSpaceBytes() } returns 0L
        coEvery { repo.listEvictableReadChapters() } returns emptyList()
        coEvery { repo.evictReadChapters() } returns EvictionSummary(0, 0L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading with empty list before upstream emits`() =
        testScope.runTest {
            // No emissions ever — the combine transform never runs.
            every { repo.observeAllDownloadedManga() } returns emptyFlow()
            every { repo.observeAllDownloads() } returns emptyFlow()

            val vm = DownloadsViewModel(repo)

            val initial = vm.uiState.value
            assertTrue(initial.isLoading)
            assertTrue(initial.mangaList.isEmpty())
        }

    @Test
    fun `groups downloads under their parent manga and clears loading flag`() =
        testScope.runTest {
            val mangaA = manga(id = 42, title = "Vagabond")
            val ch1 = download(id = "42_1", mangaId = 42, chapterNumber = 1)
            val ch2 = download(id = "42_2", mangaId = 42, chapterNumber = 2)
            every { repo.observeAllDownloadedManga() } returns flowOf(listOf(mangaA))
            every { repo.observeAllDownloads() } returns flowOf(listOf(ch1, ch2))

            val vm = DownloadsViewModel(repo)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(1, state.mangaList.size)
            assertEquals(42, state.mangaList[0].manga.mangaId)
            assertEquals(listOf("42_1", "42_2"), state.mangaList[0].chapters.map { it.id })
            assertFalse(state.isLoading)
        }

    @Test
    fun `manga with zero matching chapters are filtered out`() =
        testScope.runTest {
            // Empty manga rows are stale junk — typically left behind by an
            // interrupted "delete all" before the manga row was reaped.
            val mangaWith = manga(id = 1, title = "Has chapters")
            val mangaEmpty = manga(id = 2, title = "No chapters")
            every { repo.observeAllDownloadedManga() } returns flowOf(listOf(mangaWith, mangaEmpty))
            every { repo.observeAllDownloads() } returns
                flowOf(listOf(download(id = "1_1", mangaId = 1, chapterNumber = 1)))

            val vm = DownloadsViewModel(repo)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(listOf(1), state.mangaList.map { it.manga.mangaId })
        }

    @Test
    fun `evictable summary count and bytes are rolled up into state`() =
        testScope.runTest {
            coEvery { repo.listEvictableReadChapters() } returns
                listOf(
                    download(id = "42_1", mangaId = 42, chapterNumber = 1, fileSizeBytes = 100L),
                    download(id = "42_2", mangaId = 42, chapterNumber = 2, fileSizeBytes = 250L),
                )
            coEvery { repo.storageUsedBytes() } returns 5_000L
            coEvery { repo.availableSpaceBytes() } returns 10_000L
            every { repo.observeAllDownloadedManga() } returns flowOf(emptyList())
            every { repo.observeAllDownloads() } returns flowOf(emptyList())

            val vm = DownloadsViewModel(repo)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(2, state.readChapterCount)
            assertEquals(350L, state.readChapterBytes)
            assertEquals(5_000L, state.totalStorageUsed)
            assertEquals(10_000L, state.freeSpace)
        }

    @Test
    fun `clearReadChapters delegates to evictReadChapters on the repo`() =
        testScope.runTest {
            every { repo.observeAllDownloadedManga() } returns emptyFlow()
            every { repo.observeAllDownloads() } returns emptyFlow()
            val vm = DownloadsViewModel(repo)

            vm.clearReadChapters()
            runCurrent()

            coVerify { repo.evictReadChapters() }
        }

    @Test
    fun `deleteChapter delegates to cancelChapter with the given id`() =
        testScope.runTest {
            every { repo.observeAllDownloadedManga() } returns emptyFlow()
            every { repo.observeAllDownloads() } returns emptyFlow()
            val vm = DownloadsViewModel(repo)

            vm.deleteChapter("42_5")
            runCurrent()

            coVerify { repo.cancelChapter("42_5") }
        }

    @Test
    fun `deleteAllForManga delegates to repo with the given mangaId`() =
        testScope.runTest {
            every { repo.observeAllDownloadedManga() } returns emptyFlow()
            every { repo.observeAllDownloads() } returns emptyFlow()
            val vm = DownloadsViewModel(repo)

            vm.deleteAllForManga(42)
            runCurrent()

            coVerify { repo.deleteAllForManga(42) }
        }

    @Test
    fun `pauseDownload delegates to pauseChapter with the given id`() =
        testScope.runTest {
            every { repo.observeAllDownloadedManga() } returns emptyFlow()
            every { repo.observeAllDownloads() } returns emptyFlow()
            val vm = DownloadsViewModel(repo)

            vm.pauseDownload("42_5")
            runCurrent()

            coVerify { repo.pauseChapter("42_5") }
        }

    // -------- helpers --------

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
