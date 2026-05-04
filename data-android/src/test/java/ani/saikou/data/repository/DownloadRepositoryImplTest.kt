package ani.saikou.data.repository

import ani.saikou.data.local.db.DownloadDao
import ani.saikou.data.local.db.DownloadEntity
import ani.saikou.data.local.downloads.ChapterSizeEstimator
import ani.saikou.data.local.downloads.MangaDownloadManager
import ani.saikou.domain.model.DownloadStatus
import ani.saikou.domain.model.EvictionSummary
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the storage-boundary behaviors of [DownloadRepositoryImpl]:
 * - the persisted-string → [DownloadStatus] adapter, including the
 *   ERROR fallback when the column drifts from the enum;
 * - the legacy-row filter that hides pre-v6 rows where `chapterNumber == -1`;
 * - the eviction tally + cancel-per-id flow, including the DAO-failure
 *   degraded path that returns an empty list rather than crashing the UI.
 */
class DownloadRepositoryImplTest {
    private val dao: DownloadDao = mockk()
    private val manager: MangaDownloadManager = mockk(relaxUnitFun = true)
    private val sizeEstimator: ChapterSizeEstimator = mockk()
    private val repo = DownloadRepositoryImpl(dao, manager, sizeEstimator)

    // -------- status string → enum mapping (via getDownload) --------

    @Test
    fun `status string QUEUED maps to QUEUED enum`() = runTest { assertStatusMaps("QUEUED", DownloadStatus.QUEUED) }

    @Test
    fun `status string DOWNLOADING maps to DOWNLOADING enum`() = runTest { assertStatusMaps("DOWNLOADING", DownloadStatus.DOWNLOADING) }

    @Test
    fun `status string PAUSED maps to PAUSED enum`() = runTest { assertStatusMaps("PAUSED", DownloadStatus.PAUSED) }

    @Test
    fun `status string COMPLETED maps to COMPLETED enum`() = runTest { assertStatusMaps("COMPLETED", DownloadStatus.COMPLETED) }

    @Test
    fun `status string ERROR maps to ERROR enum`() = runTest { assertStatusMaps("ERROR", DownloadStatus.ERROR) }

    @Test
    fun `unknown status string falls back to ERROR (never null)`() =
        runTest {
            // Persisted strings can drift from the enum across schema migrations
            // — the boundary must not let null status leak into the UI.
            assertStatusMaps("WHATEVER", DownloadStatus.ERROR)
        }

    @Test
    fun `getDownload returns null when DAO has no row`() =
        runTest {
            coEvery { dao.getDownload("missing") } returns null

            assertNull(repo.getDownload("missing"))
        }

    // -------- observeDownloadsForManga --------

    @Test
    fun `observeDownloadsForManga filters out legacy rows with chapterNumber -1`() =
        runTest {
            // Pre-v6 rows can carry chapterNumber == -1; they're unreachable
            // from the chapter-number-keyed UI and must not surface.
            every { dao.getDownloadsForManga(42) } returns
                flowOf(
                    listOf(
                        entity(id = "42_-1", chapterNumber = -1),
                        entity(id = "42_5", chapterNumber = 5),
                    ),
                )

            repo.observeDownloadsForManga(42).test {
                val map = awaitItem()
                assertEquals(setOf(5), map.keys)
                assertEquals("42_5", map[5]?.id)
                awaitComplete()
            }
        }

    @Test
    fun `observeDownloadsForManga keys the result by chapterNumber`() =
        runTest {
            every { dao.getDownloadsForManga(42) } returns
                flowOf(
                    listOf(
                        entity(id = "42_3", chapterNumber = 3),
                        entity(id = "42_4", chapterNumber = 4),
                    ),
                )

            repo.observeDownloadsForManga(42).test {
                val map = awaitItem()
                assertEquals(setOf(3, 4), map.keys)
                assertEquals("42_3", map[3]?.id)
                assertEquals("42_4", map[4]?.id)
                awaitComplete()
            }
        }

    // -------- evictReadChapters --------

    @Test
    fun `evict tallies bytes and count across multiple read chapters and cancels each`() =
        runTest {
            coEvery { dao.getReadCompletedDownloads() } returns
                listOf(
                    entity(id = "42_1", chapterNumber = 1, fileSizeBytes = 100L),
                    entity(id = "42_2", chapterNumber = 2, fileSizeBytes = 200L),
                    entity(id = "42_3", chapterNumber = 3, fileSizeBytes = 350L),
                )

            val summary = repo.evictReadChapters()

            assertEquals(EvictionSummary(chaptersRemoved = 3, bytesFreed = 650L), summary)
            coVerify { manager.cancelDownload("42_1") }
            coVerify { manager.cancelDownload("42_2") }
            coVerify { manager.cancelDownload("42_3") }
        }

    @Test
    fun `evict returns zero summary and skips manager when nothing is evictable`() =
        runTest {
            coEvery { dao.getReadCompletedDownloads() } returns emptyList()

            val summary = repo.evictReadChapters()

            assertEquals(EvictionSummary(chaptersRemoved = 0, bytesFreed = 0L), summary)
            coVerify(exactly = 0) { manager.cancelDownload(any()) }
        }

    @Test
    fun `evict swallows DAO failure and returns zero summary (UI cleanup banner stays alive)`() =
        runTest {
            // A malformed reading_history row throwing in the JOIN should not
            // crash the cleanup banner — empty list is the safe degraded state.
            coEvery { dao.getReadCompletedDownloads() } throws RuntimeException("malformed row")

            val summary = repo.evictReadChapters()

            assertEquals(EvictionSummary(chaptersRemoved = 0, bytesFreed = 0L), summary)
            coVerify(exactly = 0) { manager.cancelDownload(any()) }
        }

    // -------- cancelChapterByNumber --------

    @Test
    fun `cancelChapterByNumber resolves the row and cancels by its real id`() =
        runTest {
            // Row queued via QueueNextChaptersUseCase carries the real
            // source-side id as chapterKey, so the row id is mangaId_<UUID> —
            // not mangaId_<chapterNumber>. Cancelling by chapter number must
            // still find and tear down the right row.
            coEvery { dao.getByChapterNumber(42, 217) } returns
                entity(id = "42_uuid-217", chapterNumber = 217)

            repo.cancelChapterByNumber(42, 217)

            coVerify(exactly = 1) { manager.cancelDownload("42_uuid-217") }
        }

    @Test
    fun `cancelChapterByNumber is a no-op when no row exists`() =
        runTest {
            coEvery { dao.getByChapterNumber(42, 999) } returns null

            repo.cancelChapterByNumber(42, 999)

            coVerify(exactly = 0) { manager.cancelDownload(any()) }
        }

    // -------- listEvictableReadChapters --------

    @Test
    fun `listEvictable returns empty list when DAO throws`() =
        runTest {
            coEvery { dao.getReadCompletedDownloads() } throws RuntimeException("boom")

            val out = repo.listEvictableReadChapters()

            assertTrue(out.isEmpty())
        }

    @Test
    fun `listEvictable maps DAO rows to domain Downloads`() =
        runTest {
            coEvery { dao.getReadCompletedDownloads() } returns
                listOf(entity(id = "42_5", chapterNumber = 5, status = "COMPLETED"))

            val out = repo.listEvictableReadChapters()

            assertEquals(1, out.size)
            assertEquals("42_5", out[0].id)
            assertEquals(DownloadStatus.COMPLETED, out[0].status)
        }

    // -------- helpers --------

    private suspend fun assertStatusMaps(
        persisted: String,
        expected: DownloadStatus,
    ) {
        coEvery { dao.getDownload("42_5") } returns entity(id = "42_5", chapterNumber = 5, status = persisted)

        val out = repo.getDownload("42_5")

        assertEquals(expected, out?.status)
    }

    private fun entity(
        id: String,
        chapterNumber: Int,
        status: String = "QUEUED",
        fileSizeBytes: Long = 0L,
    ) = DownloadEntity(
        id = id,
        mangaId = 42,
        mangaTitle = "Vagabond",
        chapterKey = id.substringAfterLast('_'),
        chapterNumber = chapterNumber,
        chapterName = "Chapter $chapterNumber",
        sourceId = "MangaDex",
        status = status,
        totalPages = 0,
        downloadedPages = 0,
        fileSizeBytes = fileSizeBytes,
        createdAt = 0L,
    )
}
