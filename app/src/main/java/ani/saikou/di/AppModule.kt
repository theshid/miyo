package ani.saikou.di

import android.content.Context
import ani.saikou.data.local.ConnectivityObserver
import ani.saikou.data.local.TokenStorage
import ani.saikou.data.local.db.DownloadDao
import ani.saikou.data.local.db.ReadingHistoryDao
import ani.saikou.data.local.db.SaikouDatabase
import ani.saikou.data.local.db.WatchHistoryDao
import ani.saikou.data.local.downloads.MangaDownloadManager
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.repository.AnilistRepositoryImpl
import ani.saikou.domain.repository.AnilistRepository

/**
 * Simple service locator. Keeps things lightweight without adding Hilt/Koin
 * as a dependency for now — can be swapped later if needed.
 */
object AppModule {

    private var tokenStorage: TokenStorage? = null
    private var api: AnilistApi? = null
    private var repository: AnilistRepository? = null
    private var connectivityObserver: ConnectivityObserver? = null
    private var database: SaikouDatabase? = null
    private var downloadManager: MangaDownloadManager? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        tokenStorage = TokenStorage(appContext)
        api = AnilistApi(tokenProvider = { tokenStorage!!.getToken() })
        repository = AnilistRepositoryImpl(api!!, tokenStorage!!)
        connectivityObserver = ConnectivityObserver(appContext)
        database = SaikouDatabase.getInstance(appContext)
        downloadManager = MangaDownloadManager(appContext, database!!.downloadDao())
    }

    fun repository(): AnilistRepository =
        repository ?: throw IllegalStateException("AppModule not initialized. Call init() in Application.onCreate()")

    fun tokenStorage(): TokenStorage =
        tokenStorage ?: throw IllegalStateException("AppModule not initialized.")

    fun connectivity(): ConnectivityObserver =
        connectivityObserver ?: throw IllegalStateException("AppModule not initialized.")

    fun downloadManager(): MangaDownloadManager =
        downloadManager ?: throw IllegalStateException("AppModule not initialized.")

    fun downloadDao(): DownloadDao =
        database?.downloadDao() ?: throw IllegalStateException("AppModule not initialized.")

    fun readingHistoryDao(): ReadingHistoryDao =
        database?.readingHistoryDao() ?: throw IllegalStateException("AppModule not initialized.")

    fun watchHistoryDao(): WatchHistoryDao =
        database?.watchHistoryDao() ?: throw IllegalStateException("AppModule not initialized.")
}
