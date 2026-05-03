package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.CharacterDetail
import ani.saikou.domain.repository.AnilistRepository

/**
 * "Show this character's detail page" — fetches by AniList character id
 * and returns null when the lookup fails (id unknown, network error
 * with no cache). Screen renders that as the empty state.
 */
class GetCharacterUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(id: Int): CharacterDetail? = repository.getCharacter(id)
}
