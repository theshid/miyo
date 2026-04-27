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
import ani.saikou.data.repository.MangaSourceRepositoryImpl
import ani.saikou.data.source.manga.MangaDexParser
import ani.saikou.data.source.manga.MangaPillParser
import ani.saikou.domain.repository.AnilistRepository
import ani.saikou.domain.repository.MangaSourceRepository
import ani.saikou.platform.android.log.SentryLogger
import ani.saikou.platform.log.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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
    private var httpClient: HttpClient? = null
    private var mangaDexParser: MangaDexParser? = null
    private var mangaPillParser: MangaPillParser? = null
    private var mangaSourceRepository: MangaSourceRepository? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        tokenStorage = TokenStorage(appContext)
        api = AnilistApi(tokenProvider = { tokenStorage!!.getToken() })
        repository = AnilistRepositoryImpl(api!!, tokenStorage!!)
        connectivityObserver = ConnectivityObserver(appContext)
        database = SaikouDatabase.getInstance(appContext)
        // Note: downloadManager construction moves below, after parser init —
        // it now takes MangaDexParser via constructor.
        openAiService = OpenAiService(BuildConfig.OPENAI_API_KEY)
        onboardingPrefs = OnboardingPrefs(appContext)
        feedbackService = FeedbackService()
        logger = SentryLogger()

        // Single shared Ktor client — reused across every parser. Creating
        // one per call (the old MangaDexParser() pattern) leaked OkHttp
        // connection pools on hot navigation between detail/lists/reader.
        httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            if (BuildConfig.DEBUG) {
                install(Logging) { level = LogLevel.INFO }
            }
        }
        mangaDexParser = MangaDexParser(httpClient!!, logger!!)
        mangaPillParser = MangaPillParser(logger!!)
        mangaSourceRepository = MangaSourceRepositoryImpl(
            mangaDex = mangaDexParser!!,
            mangaPill = mangaPillParser!!,
        )

        // Construct after parser init — MangaDownloadManager pulls MangaDex
        // through its constructor now (no AppModule lookup at runtime).
        downloadManager = MangaDownloadManager(appContext, database!!.downloadDao(), mangaDexParser!!)
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

    fun mangaDexParser(): MangaDexParser =
        mangaDexParser ?: throw IllegalStateException("AppModule not initialized.")

    fun mangaPillParser(): MangaPillParser =
        mangaPillParser ?: throw IllegalStateException("AppModule not initialized.")

    fun mangaSourceRepository(): MangaSourceRepository =
        mangaSourceRepository ?: throw IllegalStateException("AppModule not initialized.")
}
