package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.DownloadRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bug-fix coverage for the reader's "Save next N chapters" banner.
 * Pre-fix, the use case did `currentChapter + 1..count` blindly and
 * materialized phantom rows (e.g. chapter 221 of a 220-chapter manga),
 * which both broke the picker count and stranded the real remaining
 * chapters in ERROR. Tests below pin the clamp + real-id behavior.
 */
class QueueNextChaptersUseCaseTest {
    private val queueChapter: QueueChapterDownloadUseCase = mockk(relaxUnitFun = true)
    private val useCase = QueueNextChaptersUseCase(queueChapter)

    private fun chapter(
        id: String,
        number: Float,
    ) = Chapter(id = id, number = number, name = "Chapter $number")

    @Test
    fun `clamps to remaining chapters at the end of the manga`() =
        runTest {
            // 220-chapter manga, reader on chapter 218, banner asks for 5 more.
            val chapters = (1..220).map { chapter(id = "uuid-$it", number = it.toFloat()) }

            val queued =
                useCase(
                    mangaId = 42,
                    mangaTitle = "Vinland Saga",
                    coverUrl = null,
                    sourceId = "manga-uuid",
                    availableChapters = chapters,
                    afterChapterNumber = 218,
                    count = 5,
                )

            assertEquals(2, queued)
            coVerify(exactly = 2) { queueChapter(any()) }
        }

    @Test
    fun `queues real source-side ids, not stringified chapter numbers`() =
        runTest {
            val chapters = listOf(chapter("uuid-217", 217f), chapter("uuid-218", 218f))

            val captured = mutableListOf<DownloadRequest>()
            val captureSlot = slot<DownloadRequest>()
            coEvery { queueChapter(capture(captureSlot)) } answers { captured += captureSlot.captured }

            useCase(
                mangaId = 42,
                mangaTitle = "Vinland Saga",
                coverUrl = "https://cover",
                sourceId = "manga-uuid",
                availableChapters = chapters,
                afterChapterNumber = 216,
                count = 5,
            )

            assertEquals(listOf("uuid-217", "uuid-218"), captured.map { it.chapterKey })
            assertEquals(listOf(217, 218), captured.map { it.chapterNumber })
            assertEquals(listOf("manga-uuid", "manga-uuid"), captured.map { it.sourceId })
        }

    @Test
    fun `no-op when no chapters remain after the cursor`() =
        runTest {
            val chapters = (1..220).map { chapter("uuid-$it", it.toFloat()) }

            val queued =
                useCase(
                    mangaId = 42,
                    mangaTitle = "Vinland Saga",
                    coverUrl = null,
                    sourceId = "manga-uuid",
                    availableChapters = chapters,
                    afterChapterNumber = 220,
                    count = 5,
                )

            assertEquals(0, queued)
            coVerify(exactly = 0) { queueChapter(any()) }
        }

    @Test
    fun `no-op when the available chapter list is empty`() =
        runTest {
            val queued =
                useCase(
                    mangaId = 42,
                    mangaTitle = "Vinland Saga",
                    coverUrl = null,
                    sourceId = "",
                    availableChapters = emptyList(),
                    afterChapterNumber = 5,
                    count = 5,
                )

            assertEquals(0, queued)
            coVerify(exactly = 0) { queueChapter(any()) }
        }

    @Test
    fun `walks chapters in number order even when the source returns them shuffled`() =
        runTest {
            val chapters =
                listOf(
                    chapter("uuid-220", 220f),
                    chapter("uuid-217", 217f),
                    chapter("uuid-219", 219f),
                    chapter("uuid-218", 218f),
                )

            val captured = mutableListOf<DownloadRequest>()
            val captureSlot = slot<DownloadRequest>()
            coEvery { queueChapter(capture(captureSlot)) } answers { captured += captureSlot.captured }

            useCase(
                mangaId = 42,
                mangaTitle = "Vinland Saga",
                coverUrl = null,
                sourceId = "manga-uuid",
                availableChapters = chapters,
                afterChapterNumber = 216,
                count = 3,
            )

            assertEquals(listOf("uuid-217", "uuid-218", "uuid-219"), captured.map { it.chapterKey })
        }
}
