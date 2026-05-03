package ani.saikou.domain.source

/**
 * "Run the download queue" — kicks off the platform-side worker that
 * drains queued [ani.saikou.domain.model.DownloadRequest] rows from the
 * DB. The repo writes the row; this interface dispatches the work.
 *
 * Today the Android impl starts a foreground `Service` that pulls items
 * one-by-one. The interface keeps the service class out of presentation
 * + shared-ui (those modules can't see :app's MainActivity / R / Service
 * setup).
 */
fun interface DownloadDispatcher {
    fun dispatch()
}
