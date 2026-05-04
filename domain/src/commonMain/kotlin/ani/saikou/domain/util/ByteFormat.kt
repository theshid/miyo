package ani.saikou.domain.util

/**
 * Human-readable size string for [bytes] — picks KB/MB/GB based on
 * magnitude. Used by the reader's "download next N chapters" sheet to
 * preview disk usage before queueing.
 *
 * Pure formatter — keeps the screen Composable free of math while letting
 * the VM expose a String to the UI without a DAO-bound estimator instance.
 */
fun formatBytes(bytes: Long): String {
    val kb = bytes / KB_DIVISOR
    val mb = kb / KB_DIVISOR
    val gb = mb / KB_DIVISOR
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 10.0 -> "%.0f MB".format(mb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}

private const val KB_DIVISOR = 1024.0
