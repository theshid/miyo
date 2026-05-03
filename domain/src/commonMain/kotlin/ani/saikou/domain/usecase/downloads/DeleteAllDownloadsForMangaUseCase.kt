package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.repository.DownloadRepository

/**
 * "Delete this whole manga's downloads" — drops every chapter row +
 * page file for the given manga and removes the manga row itself.
 */
class DeleteAllDownloadsForMangaUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(mangaId: Int) {
        repository.deleteAllForManga(mangaId)
    }
}
