package ani.saikou.logging

data class CallerInfo(
    val fileName: String,
    val lineNumber: Int,
    val methodName: String,
    val className: String,
)

private val IGNORED_CLASSES = setOf(
    "ani.saikou.logging.PrettyLoggingService",
    "ani.saikou.logging.DefaultLoggingService",
    "ani.saikou.logging.LoggingService",
    "ani.saikou.logging.CallerInfoKt",
    "ani.saikou.logging.Log",
)

private val IGNORED_PREFIXES = setOf(
    "java.lang.Thread",
    "jdk.internal.reflect.",
    "java.lang.reflect.",
    "sun.reflect.",
    "dalvik.system.VMStack",
    "kotlin.coroutines.",
    "kotlinx.coroutines.",
)

fun resolveCallerInfo(): CallerInfo? {
    val stackTrace = Thread.currentThread().stackTrace
    val callerFrame = stackTrace.firstOrNull { element ->
        val name = element.className
        IGNORED_PREFIXES.none { prefix -> name.startsWith(prefix) } &&
            IGNORED_CLASSES.none { ignored ->
                name == ignored || name.startsWith("$ignored\$")
            }
    } ?: return null

    return CallerInfo(
        fileName = callerFrame.fileName ?: "Unknown",
        lineNumber = callerFrame.lineNumber,
        methodName = callerFrame.methodName,
        className = callerFrame.className.substringAfterLast('.'),
    )
}
