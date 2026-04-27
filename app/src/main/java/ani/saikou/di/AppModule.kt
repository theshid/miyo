package ani.saikou.di

import android.content.Context
import ani.saikou.BuildConfig
import ani.saikou.data.local.ConnectivityObserver
import ani.saikou.data.local.OnboardingPrefs
import ani.saikou.data.local.TokenStorage
import ani.saikou.data.local.db.ActivityEventDao
import ani.saikou.data.local.db.DownloadDao
import ani.saikou.data.local.db.ReadingHistoryDao
import ani.saikou.data.local.db.SaikouDatabase
import ani.saikou.data.local.db.WatchHistoryDao
import ani.saikou.data.local.downloads.MangaDownloadManager
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.remote.FeedbackService
import ani.saikou.data.remote.OpenAiService
import ani.saikou.data.repository.AnilistRepositoryImpl
import ani.saikou.domain.repository.AnilistRepository
import ani.saikou.platform.android.log.SentryLogger
import ani.saikou.platform.log.Logger

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
    private var openAiService: OpenAiService? = null
    private var onboardingPrefs: OnboardingPrefs? = null
    private var feedbackService: FeedbackService? = null
    private var logger: Logger? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        tokenStorage = TokenStorage(appContext)
        api = AnilistApi(tokenProvider = { tokenStorage!!.getToken() })
        repository = AnilistRepositoryImpl(api!!, tokenStorage!!)
        connectivityObserver = ConnectivityObserver(appContext)
        database = SaikouDatabase.getInstance(appContext)
        downloadManager = MangaDownloadManager(appContext, database!!.downloadDao())
        openAiService = OpenAiService(BuildConfig.OPENAI_API_KEY)
        onboardingPrefs = OnboardingPrefs(appContext)
        feedbackService = FeedbackService()
        logger = SentryLogger()
    }

    fun repository(): AnilistRepository =
        repository ?: throw IllegalStateException("AppModule not initialized. Call init() in Application.onCreate()")

    fun anilistApi(): AnilistApi =
        api ?: throw IllegalStateException("AppModule not initialized.")

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

    fun activityEventDao(): ActivityEventDao =
        database?.activityEventDao() ?: throw IllegalStateException("AppModule not initialized.")

    fun onboardingPrefs(): OnboardingPrefs =
        onboardingPrefs ?: throw IllegalStateException("AppModule not initialized.")

    fun openAiService(): OpenAiService =
        openAiService ?: throw IllegalStateException("AppModule not initialized.")

    fun feedbackService(): FeedbackService =
        feedbackService ?: throw IllegalStateException("AppModule not initialized.")

    fun logger(): Logger =
        logger ?: throw IllegalStateException("AppModule not initialized.")
}
