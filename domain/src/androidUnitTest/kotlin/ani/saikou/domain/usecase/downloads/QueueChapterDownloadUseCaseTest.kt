package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.Download
import ani.saikou.domain.model.DownloadRequest
import ani.saikou.domain.model.DownloadStatus
import ani.saikou.domain.repository.DownloadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Unit tests for the dedup branch in [QueueChapterDownloadUseCase].
 *
 * Active statuses (QUEUED / DOWNLOADING / COMPLETED) must short-circuit; idle
 * statuses (PAUSED / ERROR / no row) must call through to
 * [DownloadRepository.queueChapter]. The boundary matters because
 * regressing a row from DOWNLOADING back to QUEUED would lose progress, and
 * skipping a PAUSED row would silently break the resume affordance.
 */
class QueueChapterDownloadUseCaseTest {
    private val repo: DownloadRepository = mockk(relaxUnitFun = true)
    private val useCase = QueueChapterDownloadUseCase(repo)

    private val request =
        DownloadRequest(
            mangaId = 42,
            mangaTitle = "Vagabond",
            chapterKey = "5",
            chapterNumber = 5,
            chapterName = "Chapter 5",
            sourceId = "",
        )
    private val downloadId = "42_5"

    @Test
    fun `queues when no row exists`() =
        runTest {
            coEvery { repo.getDownload(downloadId) } returns null

            useCase(request)

            coVerify(exactly = 1) { repo.queueChapter(request) }
        }

    @Test
    fun `skips when row is QUEUED`() =
        runTest {
            coEvery { repo.getDownload(downloadId) } returns mockDownload(DownloadStatus.QUEUED)

            useCase(request)

            coVerify(exactly = 0) { repo.queueChapter(any()) }
        }

    @Test
    fun `skips when row is DOWNLOADING`() =
        runTest {
            coEvery { repo.getDownload(downloadId) } returns mockDownload(DownloadStatus.DOWNLOADING)

            useCase(request)

            coVerify(exactly = 0) { repo.queueChapter(any()) }
        }

    @Test
    fun `skips when row is COMPLETED`() =
        runTest {
            coEvery { repo.getDownload(downloadId) } returns mockDownload(DownloadStatus.COMPLETED)

            useCase(request)

            coVerify(exactly = 0) { repo.queueChapter(any()) }
        }

    @Test
    fun `re-queues a PAUSED row so resume works`() =
        runTest {
            coEvery { repo.getDownload(downloadId) } returns mockDownload(DownloadStatus.PAUSED)

            useCase(request)

            coVerify(exactly = 1) { repo.queueChapter(request) }
        }

    @Test
    fun `re-queues an ERROR row so retry works`() =
        runTest {
            coEvery { repo.getDownload(downloadId) } returns mockDownload(DownloadStatus.ERROR)

            useCase(request)

            coVerify(exactly = 1) { repo.queueChapter(request) }
        }

    private fun mockDownload(status: DownloadStatus) =
        Download(
            id = downloadId,
            mangaId = request.mangaId,
            mangaTitle = request.mangaTitle,
            chapterKey = request.chapterKey,
            chapterNumber = request.chapterNumber,
            chapterName = request.chapterName,
            sourceId = "",
            status = status,
            totalPages = 0,
            downloadedPages = 0,
            fileSizeBytes = 0,
            createdAt = 0,
        )
}
