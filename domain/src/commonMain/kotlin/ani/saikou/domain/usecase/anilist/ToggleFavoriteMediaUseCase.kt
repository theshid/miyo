package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.repository.AnilistRepository

/**
 * Flips the favorite flag on the AniList side. The two AniList mutations
 * (`ToggleFavouriteAnime` / `ToggleFavouriteManga`) live behind one repo
 * method; the caller picks the kind via [isAnime].
 */
class ToggleFavoriteMediaUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(
        mediaId: Int,
        isAnime: Boolean,
    ) {
        repository.toggleFavorite(mediaId, isAnime)
    }
}
