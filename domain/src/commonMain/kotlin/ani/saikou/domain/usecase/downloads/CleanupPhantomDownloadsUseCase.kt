package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.repository.DownloadRepository

/**
 * Tear down stranded phantom download rows for [mangaId] — entries whose
 * chapter number is above [maxKnownChapter] and never reached COMPLETED.
 * The reader VM calls this whenever a fresh source-side chapter list is
 * resolved, undoing the pre-fix "save next N" banner that queued bogus
 * chapter numbers past the real end of the manga.
 */
class CleanupPhantomDownloadsUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(
        mangaId: Int,
        maxKnownChapter: Int,
    ) {
        repository.cleanupPhantomDownloads(mangaId, maxKnownChapter)
    }
}
