package ani.saikou.domain.model

/**
 * Lifecycle states a downloaded chapter can be in. Values match the strings
 * persisted in the Room `downloads.status` column — [fromString] does the
 * adapter's job at the storage boundary so the enum can flow freely
 * through the rest of the layers.
 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR,
    ;

    companion object {
        fun fromString(value: String?): DownloadStatus? =
            when (value) {
                "QUEUED" -> QUEUED
                "DOWNLOADING" -> DOWNLOADING
                "PAUSED" -> PAUSED
                "COMPLETED" -> COMPLETED
                "ERROR" -> ERROR
                else -> null
            }
    }
}
