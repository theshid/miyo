package ani.saikou.domain.model.anime

/**
 * Outcome of a single call into [`ani.saikou.domain.repository.AnimeSourceRepository`].
 * A typed return is preferable to silently swallowing errors into empty lists,
 * because the UI needs to render different copy for "anime not on this source"
 * (legitimate empty success) vs. "anime source is blocking us" (typed failure).
 *
 * Use [fold] / `when` at the call site — both branches are exhaustively covered.
 */
sealed interface AnimeSourceResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AnimeSourceResult<T>

    data class Failed(
        val failure: AnimeSourceFailure,
    ) : AnimeSourceResult<Nothing>
}

inline fun <T, R> AnimeSourceResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AnimeSourceFailure) -> R,
): R =
    when (this) {
        is AnimeSourceResult.Success -> onSuccess(value)
        is AnimeSourceResult.Failed -> onFailure(failure)
    }
