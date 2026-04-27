package ani.saikou.data.android.di

import ani.saikou.data.local.db.SaikouDatabase
import ani.saikou.data.local.downloads.ChapterSizeEstimator
import ani.saikou.data.local.downloads.MangaDownloadManager
import ani.saikou.data.repository.DownloadRepositoryImpl
import ani.saikou.data.repository.MangaSourceRepositoryImpl
import ani.saikou.data.source.manga.MangaDexParser
import ani.saikou.data.source.manga.MangaPillParser
import ani.saikou.domain.repository.DownloadRepository
import ani.saikou.domain.repository.MangaSourceRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Marker for the build-debug-flag binding `:app` provides. */
const val IS_DEBUG_QUALIFIER = "isDebug"

/**
 * Android-specific bindings: HTTP client (with OkHttp engine), Room database
 * + DAOs, JVM-only parsers, downloads orchestrator, and the repos that
 * depend on any of the above.
 */
val dataAndroidModule = module {
    // ─── Networking ─────────────────────────────────────────────────────
    // Single shared client across every parser. ContentNegotiation lets
    // Ktor return parsed JSON directly when callers ask for typed bodies;
    // the existing parsers prefer raw `bodyAsText()` so the negotiator is
    // currently inert but keeps the door open for typed adapters later.
    // Logging is gated on the named "isDebug" boolean :app provides.
    single<HttpClient> {
        val isDebug = get<Boolean>(named(IS_DEBUG_QUALIFIER))
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            if (isDebug) {
                install(Logging) { level = LogLevel.INFO }
            }
        }
    }

    // ─── Persistence ────────────────────────────────────────────────────
    single { SaikouDatabase.getInstance(androidContext()) }
    single { get<SaikouDatabase>().downloadDao() }
    single { get<SaikouDatabase>().readingHistoryDao() }
    single { get<SaikouDatabase>().watchHistoryDao() }
    single { get<SaikouDatabase>().activityEventDao() }

    // ─── Source parsers (JVM-only) ──────────────────────────────────────
    singleOf(::MangaPillParser)

    // ─── Downloads ──────────────────────────────────────────────────────
    singleOf(::ChapterSizeEstimator)
    single {
        MangaDownloadManager(
            context = androidContext(),
            dao = get(),
            mangaDex = get<MangaDexParser>(),
        )
    }
    single<DownloadRepository> {
        DownloadRepositoryImpl(
            dao = get(),
            manager = get(),
            sizeEstimator = get(),
        )
    }

    // ─── Source aggregation repository ──────────────────────────────────
    // Lives here (not in :data commonMain) because it constructs against
    // MangaPillParser, which is JVM-only.
    single<MangaSourceRepository> {
        MangaSourceRepositoryImpl(
            mangaDex = get<MangaDexParser>(),
            mangaPill = get<MangaPillParser>(),
        )
    }
}
