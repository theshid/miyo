package ani.saikou.logging

interface LoggingService {
    fun log(
        message: String,
        tag: String? = null,
        level: MonitoringLogLevel = MonitoringLogLevel.Debug,
        error: Throwable? = null,
    )
}

class DefaultLoggingService : LoggingService {
    override fun log(message: String, tag: String?, level: MonitoringLogLevel, error: Throwable?) {
        println("${tag?.let { "$it - " } ?: ""}$message")
        error?.printStackTrace()
    }
}
