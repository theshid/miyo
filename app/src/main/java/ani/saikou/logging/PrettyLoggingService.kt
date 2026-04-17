package ani.saikou.logging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Box-drawing logger that prints structured, bordered logs with caller info,
 * thread name, and emoji-prefixed level labels. Used in debug builds.
 *
 * Features:
 * - Min level filtering
 * - Auto-detection and pretty-printing of JSON content
 * - Emoji-coded level tags for fast scanning
 * - Crash breadcrumb recording
 */
class PrettyLoggingService(
    private val minLevel: MonitoringLogLevel = MonitoringLogLevel.Debug,
) : LoggingService {

    private val jsonPretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    override fun log(message: String, tag: String?, level: MonitoringLogLevel, error: Throwable?) {
        if (level.ordinal < minLevel.ordinal) return

        // Record to breadcrumb buffer
        LogBreadcrumbs.record(level, tag, message)

        val logcatTag = tag ?: DEFAULT_TAG
        val caller = resolveCallerInfo()
        val prettyMessage = tryPrettyPrintJson(message)

        val lines = buildList {
            add(TOP_BORDER)
            add("$LEFT_BORDER ${level.emoji} ${level.label}  $THIN_SEPARATOR  Thread: ${Thread.currentThread().name}")
            add(MIDDLE_BORDER)
            if (caller != null) {
                add("$LEFT_BORDER $ARROW .${caller.className}.${caller.methodName}(${caller.fileName}:${caller.lineNumber})")
                add(MIDDLE_BORDER)
            }
            prettyMessage.lines().forEach { line ->
                add("$LEFT_BORDER $line")
            }
            if (error != null) {
                add(MIDDLE_BORDER)
                add("$LEFT_BORDER ${error::class.simpleName}: ${error.message}")
                error.stackTraceToString().lines().take(MAX_STACK_LINES).forEach { line ->
                    add("$LEFT_BORDER   $line")
                }
            }
            add(BOTTOM_BORDER)
        }
        lines.forEach { line -> platformLog(logcatTag, line, level) }
    }

    private fun tryPrettyPrintJson(message: String): String {
        val trimmed = message.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return message
        return try {
            val element = jsonPretty.parseToJsonElement(trimmed)
            jsonPretty.encodeToString(JsonElement.serializer(), element)
        } catch (_: Exception) {
            message
        }
    }

    companion object {
        private const val TOP_LEFT_CORNER = '\u250C'
        private const val BOTTOM_LEFT_CORNER = '\u2514'
        private const val MIDDLE_CORNER = '\u251C'
        private const val LEFT_BORDER = '\u2502'
        private const val DOUBLE_LINE = "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" +
            "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" +
            "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" +
            "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" +
            "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" +
            "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"
        private const val SINGLE_LINE = "\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504" +
            "\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504" +
            "\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504" +
            "\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504" +
            "\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504" +
            "\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504\u2504"
        private val TOP_BORDER = "$TOP_LEFT_CORNER$DOUBLE_LINE"
        private val BOTTOM_BORDER = "$BOTTOM_LEFT_CORNER$DOUBLE_LINE"
        private val MIDDLE_BORDER = "$MIDDLE_CORNER$SINGLE_LINE"
        private const val THIN_SEPARATOR = "\u2502"
        private const val ARROW = "\u2192"
        private const val DEFAULT_TAG = "Miyo"
        private const val MAX_STACK_LINES = 15
    }
}

internal fun platformLog(tag: String, message: String, level: MonitoringLogLevel) {
    when (level) {
        MonitoringLogLevel.Debug -> android.util.Log.d(tag, message)
        MonitoringLogLevel.Info -> android.util.Log.i(tag, message)
        MonitoringLogLevel.Warning -> android.util.Log.w(tag, message)
        MonitoringLogLevel.Error -> android.util.Log.e(tag, message)
        MonitoringLogLevel.Critical -> android.util.Log.wtf(tag, message)
    }
}
