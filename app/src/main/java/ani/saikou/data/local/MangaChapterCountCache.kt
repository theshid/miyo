package ani.saikou.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-lifetime cache of "chapter counts we figured out from sources".
 *
 * AniList sometimes returns null for a manga's `chapters` field (licensed
 * titles, on-hiatus releases). The detail screen probes MangaDex/MangaPill
 * to find the real total, but that result was previously thrown away when
 * the user navigated away. This cache keeps it around so other screens —
 * the user's lists, continue-reading shelf, etc. — render the actual
 * number instead of "?".
 *
 * Cache is intentionally not persisted: source data drifts (chapters get
 * added), so re-probing on next app launch is preferable to showing a
 * stale number.
 */
object MangaChapterCountCache {

    private val _counts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val counts: StateFlow<Map<Int, Int>> = _counts.asStateFlow()

    fun put(mediaId: Int, count: Int) {
        if (count <= 0) return
        _counts.value = _counts.value + (mediaId to count)
    }

    fun get(mediaId: Int): Int? = _counts.value[mediaId]
}
