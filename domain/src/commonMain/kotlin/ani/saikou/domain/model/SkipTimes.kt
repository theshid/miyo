package ani.saikou.domain.model

/**
 * Crowd-sourced opening + ending skip intervals for a specific episode.
 * All four fields are seconds offsets; null means "no data for this kind"
 * (the API doesn't always have OP and ED entries — sometimes just one).
 *
 * Use [EMPTY] when the API call fails or returns nothing — players
 * should treat that as "show no skip controls" without crashing.
 */
data class SkipTimes(
    val opStartSec: Float? = null,
    val opEndSec: Float? = null,
    val edStartSec: Float? = null,
    val edEndSec: Float? = null,
) {
    companion object {
        val EMPTY = SkipTimes()
    }
}
