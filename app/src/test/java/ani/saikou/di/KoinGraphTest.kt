package ani.saikou.di

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import ani.saikou.data.android.di.dataAndroidModule
import ani.saikou.data.di.dataModule
import ani.saikou.data.local.ConnectivityObserver
import ani.saikou.data.local.OnboardingPrefs
import ani.saikou.data.local.TokenStorage
import ani.saikou.data.local.db.ActivityEventDao
import ani.saikou.data.local.db.DownloadDao
import ani.saikou.data.local.db.ReadingHistoryDao
import ani.saikou.data.local.db.WatchHistoryDao
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.remote.FeedbackService
import ani.saikou.data.remote.OpenAiService
import ani.saikou.data.source.manga.MangaDexParser
import ani.saikou.data.source.manga.MangaPillParser
import ani.saikou.domain.repository.AnilistRepository
import ani.saikou.domain.repository.DownloadRepository
import ani.saikou.domain.repository.MangaSourceRepository
import ani.saikou.domain.usecase.downloads.QueueChapterDownloadUseCase
import ani.saikou.domain.usecase.downloads.QueueNextChaptersUseCase
import ani.saikou.platform.android.di.platformAndroidModule
import ani.saikou.platform.log.Logger
import io.ktor.client.HttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Boots the Koin graph that MiyoApplication.onCreate would and resolves a
 * representative slice of bindings. A missing definition or circular dep
 * would fail at startKoin{} or the first `inject<T>()` access — this test
 * catches both before the app launches in production.
 *
 * Each layer's "load-bearing" types are listed; ViewModel bindings live
 * behind the savedStateHandle factory so they're not directly resolved
 * here (they're exercised in their own tests).
 */
/**
 * Stubbed Application — defaulting to MiyoApplication would trigger its
 * full onCreate (Sentry, WorkManager scheduling, etc.), which breaks the
 * test by reaching into uninitialised platform APIs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class KoinGraphTest : KoinTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `every layered module resolves its core bindings`() {
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(appModule, platformAndroidModule, dataModule, dataAndroidModule)
        }

        // The actual assertion is that touching each lazy `inject<T>()`
        // delegate triggers resolution — Koin throws InstanceCreationException
        // on a missing binding, failing the test before we reach `.javaClass`.
        // Each `.javaClass` call forces the lazy to resolve.

        // platform-android
        inject<Logger>().value.javaClass

        // data + data-android
        inject<HttpClient>().value.javaClass
        inject<MangaDexParser>().value.javaClass
        inject<MangaPillParser>().value.javaClass
        inject<DownloadRepository>().value.javaClass
        inject<MangaSourceRepository>().value.javaClass
        inject<DownloadDao>().value.javaClass
        inject<ReadingHistoryDao>().value.javaClass
        inject<WatchHistoryDao>().value.javaClass
        inject<ActivityEventDao>().value.javaClass

        // app
        inject<TokenStorage>().value.javaClass
        inject<AnilistApi>().value.javaClass
        inject<AnilistRepository>().value.javaClass
        inject<ConnectivityObserver>().value.javaClass
        inject<OnboardingPrefs>().value.javaClass
        inject<OpenAiService>().value.javaClass
        inject<FeedbackService>().value.javaClass

        // use cases
        inject<QueueChapterDownloadUseCase>().value.javaClass
        inject<QueueNextChaptersUseCase>().value.javaClass
    }
}
