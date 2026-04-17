package ani.saikou.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Circular buffer that stores the last [maxEntries] log entries in memory.
 * On crash or on demand, dumps them to a file for post-mortem analysis.
 *
 * Usage:
 *   LogBreadcrumbs.record(level, tag, message)   // called by LoggingService
 *   LogBreadcrumbs.dumpToFile(context)            // call from UncaughtExceptionHandler
 *   LogBreadcrumbs.getEntries()                   // read current buffer
 */
object LogBreadcrumbs {

    private const val MAX_ENTRIES = 50
    private val buffer = ConcurrentLinkedDeque<BreadcrumbEntry>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    data class BreadcrumbEntry(
        val timestamp: Long,
        val level: MonitoringLogLevel,
        val tag: String?,
        val message: String,
        val threadName: String,
    ) {
        fun formatted(): String {
            val time = timeFormat.format(Date(timestamp))
            val prefix = "${level.emoji} ${level.label}"
            val tagStr = tag?.let { "[$it] " } ?: ""
            return "$time  $prefix  $tagStr$message  (thread: $threadName)"
        }
    }

    fun record(level: MonitoringLogLevel, tag: String?, message: String) {
        val entry = BreadcrumbEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            threadName = Thread.currentThread().name,
        )
        buffer.addLast(entry)
        while (buffer.size > MAX_ENTRIES) {
            buffer.pollFirst()
        }
    }

    fun getEntries(): List<BreadcrumbEntry> = buffer.toList()

    fun dumpToFile(context: Context): File? {
        return try {
            val entries = getEntries()
            if (entries.isEmpty()) return null

            val dir = File(context.filesDir, "crash_logs")
            dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(dir, "breadcrumbs_$timestamp.txt")

            file.writeText(buildString {
                appendLine("=== Miyo Log Breadcrumbs ===")
                appendLine("Dumped at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                appendLine("Entries: ${entries.size}")
                appendLine()
                entries.forEach { entry ->
                    appendLine(entry.formatted())
                }
            })
            file
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        buffer.clear()
    }
}
