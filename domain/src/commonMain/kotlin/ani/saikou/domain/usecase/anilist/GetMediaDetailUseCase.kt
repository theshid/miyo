package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AnilistRepository

class GetMediaDetailUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(id: Int): Media? = repository.getMedia(id)
}
