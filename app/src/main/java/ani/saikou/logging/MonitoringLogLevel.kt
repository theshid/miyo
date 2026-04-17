package ani.saikou.logging

enum class MonitoringLogLevel {
    Debug,
    Info,
    Warning,
    Error,
    Critical;

    val emoji: String
        get() = when (this) {
            Debug -> "\uD83D\uDC1B"     // 🐛
            Info -> "\u2139\uFE0F"       // ℹ️
            Warning -> "\u26A0\uFE0F"    // ⚠️
            Error -> "\u274C"            // ❌
            Critical -> "\uD83D\uDD25"   // 🔥
        }

    val label: String
        get() = when (this) {
            Debug -> "DEBUG"
            Info -> "INFO"
            Warning -> "WARN"
            Error -> "ERROR"
            Critical -> "CRITICAL"
        }
}
