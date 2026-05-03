package ani.saikou.domain.repository

import ani.saikou.domain.model.AnimeSearchResult

/**
 * Aggregator over the configured anime stream sources. Today there's only
 * one provider (GogoAnime), but the interface lets future sources drop in
 * without the VM/use cases noticing — same shape as
 * [MangaSourceRepository] for the manga side.
 */
interface AnimeSourceRepository {
    /** Search the source catalog for the given title; results are best-effort. */
    suspend fun search(query: String): List<AnimeSearchResult>
}
