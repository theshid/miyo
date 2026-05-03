package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.repository.AnilistRepository

/**
 * "Update my list entry for this media" — partial-update of progress / score /
 * status. Any null arg is left unchanged on AniList.
 */
class EditListEntryUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(
        mediaId: Int,
        progress: Int? = null,
        score: Int? = null,
        status: String? = null,
    ) {
        repository.editListEntry(mediaId, progress, score, status)
    }
}
