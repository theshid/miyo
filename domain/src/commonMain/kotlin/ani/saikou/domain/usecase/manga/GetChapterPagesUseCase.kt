package ani.saikou.domain.usecase.manga

import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.repository.MangaSourceRepository

class GetChapterPagesUseCase(
    private val repository: MangaSourceRepository,
) {
    suspend operator fun invoke(chapterId: String): List<MangaPage> = repository.getPages(chapterId)
}
