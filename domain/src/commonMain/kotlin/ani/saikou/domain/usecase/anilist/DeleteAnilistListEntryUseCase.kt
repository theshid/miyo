package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.repository.AnilistRepository

/**
 * Removes the user's list entry for a media item. [listId] is AniList's
 * MediaListEntry ID — distinct from the media ID — and is fetched from
 * the parent media when the entry is loaded.
 */
class DeleteAnilistListEntryUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(listId: Int) {
        repository.deleteListEntry(listId)
    }
}
