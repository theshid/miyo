package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.repository.MangaSourceRepository

/**
 * "How many chapters does this manga actually have?" — used for AniList-null
 * titles where the user's list would otherwise show no total. Backed by the
 * source aggregator (MangaDex / MangaPill); the result is intended to be
 * stashed in [ani.saikou.domain.cache.MangaChapterCountCache] for cross-
 * screen reuse.
 *
 * @param anilistTotal Pass the AniList-reported total when known so the
 *   aggregator can detect partial-catalog cases; null when unknown.
 */
class ResolveChapterCountUseCase(
    private val repository: MangaSourceRepository,
) {
    suspend operator fun invoke(
        title: String,
        anilistTotal: Int? = null,
    ): Int? = repository.resolveChapterCount(title, anilistTotal)
}
