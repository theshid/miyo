package ani.saikou.di

import ani.saikou.BuildConfig
import ani.saikou.data.android.di.IS_DEBUG_QUALIFIER
import ani.saikou.data.local.ConnectivityObserver
import ani.saikou.data.local.OnboardingPrefs
import ani.saikou.data.local.TokenStorage
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.remote.FeedbackService
import ani.saikou.data.remote.OpenAiService
import ani.saikou.data.repository.AnilistRepositoryImpl
import ani.saikou.domain.repository.AnilistRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Bindings that haven't yet been pulled into the layered modules — utilities
 * and services still living under `app/src/main/java/ani/saikou/data/local`
 * and `…/data/remote` because the file moves are scheduled for later
 * commits. Each `single { ... }` here mirrors a slot AppModule used to fill.
 */
val appModule = module {
    // ─── Build config (consumed by :data-android's HttpClient binding) ─
    single<Boolean>(named(IS_DEBUG_QUALIFIER)) { BuildConfig.DEBUG }

    // ─── Auth + AniList ────────────────────────────────────────────────
    single { TokenStorage(androidContext()) }
    single {
        // AnilistApi takes a tokenProvider lambda — we wire it to the
        // TokenStorage singleton so a token rotation is reflected in
        // subsequent calls.
        AnilistApi(tokenProvider = { get<TokenStorage>().getToken() })
    }
    single<AnilistRepository> { AnilistRepositoryImpl(get(), get()) }

    // ─── Device / process services still living in :app ───────────────
    single { ConnectivityObserver(androidContext()) }
    single { OnboardingPrefs(androidContext()) }

    // ─── Remote services ───────────────────────────────────────────────
    single { OpenAiService(BuildConfig.OPENAI_API_KEY) }
    singleOf(::FeedbackService)
}
