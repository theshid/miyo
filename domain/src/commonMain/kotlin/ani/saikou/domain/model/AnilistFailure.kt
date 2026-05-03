package ani.saikou.domain.model

/**
 * What went wrong on the last AniList round-trip after retries were
 * exhausted. The AniList client publishes this on a side channel so that
 * UI screens can disambiguate "AniList legitimately returned no results"
 * from "the network failed" without making the success path return Result.
 *
 * Cleared on the next successful call.
 */
sealed class AnilistFailure {
    data class Network(
        val cause: Throwable,
    ) : AnilistFailure()

    data class Server(
        val httpStatus: Int,
    ) : AnilistFailure()

    data class Other(
        val cause: Throwable,
    ) : AnilistFailure()
}
