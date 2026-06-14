package ani.saikou.data.repository

import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.model.anime.AnimeSourceFailure
import ani.saikou.domain.model.anime.AnimeSourceResult
import ani.saikou.domain.repository.AnimeSourceRepository
import ani.saikou.domain.source.AnimeProvider
import ani.saikou.platform.log.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Multi-provider orchestrator. Replaces the previous single-provider
 * (anineko-only) shape — each [`AnimeProvider`] is queried in parallel for
 * search; episode / stream lookups dispatch back to the right provider via
 * the slug or URL.
 *
 * Slug encoding:
 *   `<providerId>:<bareSlug>`
 *
 * Legacy bare slugs (no `:`) saved by older app versions default to anineko —
 * that's where every pre-1.4.0 watch history entry came from. The encoded
 * form lives only at the repository boundary; providers never see it.
 */
class AnimeSourceRepositoryImpl(
    private val providers: List<AnimeProvider>,
    private val logger: Logger,
) : AnimeSourceRepository {
    /**
     * Fan out the query across every provider in parallel, prefix each
     * returned slug with the provider id, then concatenate. The repository
     * surfaces a Failed result only when EVERY provider failed; one provider
     * returning results overrides another's transient outage.
     */
    override suspend fun search(query: String): AnimeSourceResult<List<AnimeSearchResult>> {
        if (providers.isEmpty()) return AnimeSourceResult.Success(emptyList())

        val outcomes =
            coroutineScope {
                providers
                    .map { provider ->
                        async { provider to provider.search(query) }
                    }.awaitAll()
            }

        val results = mutableListOf<AnimeSearchResult>()
        val failures = mutableListOf<Pair<AnimeProvider, AnimeSourceFailure>>()
        for ((provider, outcome) in outcomes) {
            when (outcome) {
                is AnimeSourceResult.Failed -> failures.add(provider to outcome.failure)
                is AnimeSourceResult.Success -> {
                    for (entry in outcome.value) {
                        results.add(entry.copy(slug = encodeSlug(provider.id, entry.slug)))
                    }
                }
            }
        }

        if (results.isEmpty() && failures.isNotEmpty()) {
            logger.reportWarning(
                area = AREA,
                method = "search",
                message = "every provider failed",
                extras =
                    mapOf(
                        "query" to query,
                        "failed_providers" to failures.joinToString(",") { it.first.id },
                    ),
            )
            // Prefer the first non-transport failure so the UI surfaces the
            // most actionable copy (Blocked > Unavailable > ContractChanged >
            // TransportError). Transport errors are the least specific.
            val preferred =
                failures.firstOrNull { it.second is AnimeSourceFailure.Blocked }
                    ?: failures.firstOrNull { it.second is AnimeSourceFailure.Unavailable }
                    ?: failures.firstOrNull { it.second is AnimeSourceFailure.ContractChanged }
                    ?: failures.first()
            return AnimeSourceResult.Failed(preferred.second)
        }
        return AnimeSourceResult.Success(results)
    }

    /**
     * Decodes the slug prefix, finds the matching provider, and forwards.
     * Unknown provider id (e.g. a saved slug whose provider was removed)
     * surfaces as Unavailable so the player can guide the user toward picking
     * a new source.
     */
    override suspend fun getEpisodes(slug: String): AnimeSourceResult<List<Episode>> {
        val (providerId, bareSlug) = decodeSlug(slug)
        val provider =
            providers.firstOrNull { it.id == providerId }
                ?: return unknownProvider(providerId, "getEpisodes", mapOf("slug" to slug))
        return provider.getEpisodes(bareSlug)
    }

    /**
     * Dispatches via the URL's host — each provider's episode URLs land on
     * a distinct origin so this is the cheapest correct routing key. When no
     * provider claims the host, fall back to the first provider for
     * defensive compatibility.
     */
    override suspend fun getStreamLinks(episodeUrl: String): AnimeSourceResult<List<StreamLink>> {
        val provider =
            providers.firstOrNull { episodeUrl.contains(it.host, ignoreCase = true) }
                ?: providers.firstOrNull()
                ?: return AnimeSourceResult.Failed(AnimeSourceFailure.Unavailable(0))
        return provider.getStreamLinks(episodeUrl)
    }

    private fun unknownProvider(
        providerId: String,
        method: String,
        extras: Map<String, String>,
    ): AnimeSourceResult<Nothing> {
        logger.reportWarning(
            area = AREA,
            method = method,
            message = "no provider registered for id '$providerId'",
            extras = extras + ("provider_id" to providerId),
        )
        return AnimeSourceResult.Failed(AnimeSourceFailure.Unavailable(0))
    }

    private fun encodeSlug(
        providerId: String,
        bareSlug: String,
    ): String = "$providerId$ENCODE_SEPARATOR$bareSlug"

    private fun decodeSlug(maybeEncoded: String): Pair<String, String> {
        val idx = maybeEncoded.indexOf(ENCODE_SEPARATOR)
        return if (idx < 0) {
            // Legacy bare slug — every pre-1.4.0 history entry is from anineko.
            LEGACY_PROVIDER_ID to maybeEncoded
        } else {
            maybeEncoded.substring(0, idx) to maybeEncoded.substring(idx + 1)
        }
    }

    private companion object {
        private const val AREA = "AnimeSourceRepository"
        private const val ENCODE_SEPARATOR = ":"
        private const val LEGACY_PROVIDER_ID = "anineko"
    }
}
