package ani.saikou.di

import android.content.Context
import ani.saikou.data.local.ConnectivityObserver
import ani.saikou.data.local.OnboardingPrefs
import ani.saikou.data.local.TokenStorage
import ani.saikou.data.local.db.ActivityEventDao
import ani.saikou.data.local.db.DownloadDao
import ani.saikou.data.local.db.ReadingHistoryDao
import ani.saikou.data.local.db.WatchHistoryDao
import ani.saikou.data.local.downloads.MangaDownloadManager
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.remote.FeedbackService
import ani.saikou.data.remote.OpenAiService
import ani.saikou.data.source.manga.MangaDexParser
import ani.saikou.data.source.manga.MangaPillParser
import ani.saikou.domain.repository.AnilistRepository
import ani.saikou.domain.repository.DownloadRepository
import ani.saikou.domain.repository.MangaSourceRepository
import ani.saikou.platform.log.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Thin facade over the Koin DI graph. Predates Koin in this codebase and is
 * being retired feature-by-feature in subsequent commits — each ViewModel
 * that switches to constructor injection deletes one accessor here.
 *
 * Until that's done, every existing `AppModule.X()` call site keeps working
 * because [resolve] just returns whatever Koin would have given a directly-
 * injected consumer.
 */
object AppModule : KoinComponent {

    /**
     * No-op kept so [ani.saikou.MiyoApplication] doesn't have to change its
     * init order. Koin's `startKoin { ... }` runs immediately before this
     * call, so the graph is already wired by the time anyone reaches the
     * accessors below.
     */
    fun init(context: Context) {
        // Koin owns construction now. Application calls startKoin {} above
        // this line; nothing to do here.
    }

    fun repository(): AnilistRepository = get()
    fun anilistApi(): AnilistApi = get()
    fun tokenStorage(): TokenStorage = get()
    fun connectivity(): ConnectivityObserver = get()
    fun downloadManager(): MangaDownloadManager = get()
    fun downloadDao(): DownloadDao = get()
    fun readingHistoryDao(): ReadingHistoryDao = get()
    fun watchHistoryDao(): WatchHistoryDao = get()
    fun activityEventDao(): ActivityEventDao = get()
    fun onboardingPrefs(): OnboardingPrefs = get()
    fun openAiService(): OpenAiService = get()
    fun feedbackService(): FeedbackService = get()
    fun logger(): Logger = get()
    fun mangaDexParser(): MangaDexParser = get()
    fun mangaPillParser(): MangaPillParser = get()
    fun mangaSourceRepository(): MangaSourceRepository = get()
    fun downloadRepository(): DownloadRepository = get()
}
