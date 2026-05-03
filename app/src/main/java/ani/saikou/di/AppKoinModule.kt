package ani.saikou.di

import ani.saikou.BuildConfig
import ani.saikou.data.android.di.IS_DEBUG_QUALIFIER
import ani.saikou.data.local.ConnectivityObserver
import ani.saikou.data.local.OnboardingPrefs
import ani.saikou.data.local.TokenStorage
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.remote.FeedbackServiceImpl
import ani.saikou.data.remote.OpenAiService
import ani.saikou.data.remote.news.ANNNewsSource
import ani.saikou.data.remote.news.JikanNewsSource
import ani.saikou.data.remote.news.RedditNewsSource
import ani.saikou.data.repository.AnilistRepositoryImpl
import ani.saikou.data.repository.AuthRepositoryImpl
import ani.saikou.data.repository.FeedbackRepositoryImpl
import ani.saikou.data.repository.NewsRepositoryImpl
import ani.saikou.domain.repository.AnilistRepository
import ani.saikou.domain.repository.AuthRepository
import ani.saikou.domain.repository.FeedbackRepository
import ani.saikou.domain.repository.NewsRepository
import ani.saikou.domain.source.FeedbackService
import ani.saikou.domain.source.NewsSource
import ani.saikou.domain.usecase.auth.GetAnilistAuthUrlUseCase
import ani.saikou.domain.usecase.downloads.QueueChapterDownloadUseCase
import ani.saikou.domain.usecase.downloads.QueueNextChaptersUseCase
import ani.saikou.domain.usecase.feedback.SubmitFeedbackUseCase
import ani.saikou.domain.usecase.news.GetAiringScheduleUseCase
import ani.saikou.domain.usecase.news.GetLatestNewsUseCase
import ani.saikou.presentation.screens.feedback.FeedbackViewModel
import ani.saikou.presentation.screens.login.LoginViewModel
import ani.saikou.presentation.screens.news.NewsFeedViewModel
import ani.saikou.screens.ai.AiChatViewModel
import ani.saikou.screens.anime.AnimeViewModel
import ani.saikou.screens.character.CharacterDetailViewModel
import ani.saikou.screens.detail.MediaDetailViewModel
import ani.saikou.screens.downloads.DownloadsViewModel
import ani.saikou.screens.home.HomeViewModel
import ani.saikou.screens.lists.UserListsViewModel
import ani.saikou.screens.manga.MangaViewModel
import ani.saikou.screens.player.VideoPlayerViewModel
import ani.saikou.screens.reader.MangaReaderViewModel
import ani.saikou.screens.search.SearchViewModel
import ani.saikou.screens.seasonal.SeasonalCalendarViewModel
import ani.saikou.screens.stats.StatsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Bindings that haven't yet been pulled into the layered modules — utilities
 * and services still living under `app/src/main/java/ani/saikou/data/local`
 * and `…/data/remote` because the file moves are scheduled for later
 * commits. Each `single { ... }` here mirrors a slot AppModule used to fill.
 */
val appModule =
    module {
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
        // Auth flow — VM (via use case) reaches AuthRepository to build the
        // OAuth URL. CLIENT_ID stays in :app's AnilistApi for now; once the
        // API client moves to :data with a domain-side AuthConfig, the
        // injected literal collapses into a single source.
        single<AuthRepository> { AuthRepositoryImpl(anilistClientId = AnilistApi.CLIENT_ID) }

        // ─── Device / process services still living in :app ───────────────
        single { ConnectivityObserver(androidContext()) }
        single { OnboardingPrefs(androidContext()) }

        // ─── Remote services ───────────────────────────────────────────────
        single { OpenAiService(BuildConfig.OPENAI_API_KEY) }
        single<FeedbackService> {
            FeedbackServiceImpl(
                webhookUrl = BuildConfig.DISCORD_FEEDBACK_WEBHOOK,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
            )
        }
        // Repository wraps the service so VMs (via use cases) never see the
        // outbound transport directly.
        single<FeedbackRepository> { FeedbackRepositoryImpl(get()) }

        // ─── News sources + repository ────────────────────────────────────
        // Three sources fan-out into a deduped, sorted feed via the repo.
        // Jikan is also exposed by name because it's the only source that
        // backs the airing schedule (kept as a typed dep, not a NewsSource
        // lookup, so the contract is explicit).
        single { JikanNewsSource() }
        single { RedditNewsSource() }
        single { ANNNewsSource() }
        single<NewsRepository> {
            NewsRepositoryImpl(
                sources = listOf<NewsSource>(get<JikanNewsSource>(), get<RedditNewsSource>(), get<ANNNewsSource>()),
                jikan = get(),
            )
        }

        // ─── Use cases ─────────────────────────────────────────────────────
        // factoryOf — fresh instance per resolution. Use cases are stateless
        // wrappers and don't benefit from singleton-ness; per-call alloc keeps
        // the door open for parameterized state if a future use case needs it.
        factoryOf(::GetAiringScheduleUseCase)
        factoryOf(::GetAnilistAuthUrlUseCase)
        factoryOf(::GetLatestNewsUseCase)
        factoryOf(::QueueChapterDownloadUseCase)
        factoryOf(::QueueNextChaptersUseCase)
        factoryOf(::SubmitFeedbackUseCase)

        // ─── ViewModels ────────────────────────────────────────────────────
        // Every Compose-backed ViewModel resolves through Koin now.
        // viewModelOf reflects against the constructor and pulls each param
        // from the graph (including SavedStateHandle for VMs that take it).
        viewModelOf(::AiChatViewModel)
        viewModelOf(::AnimeViewModel)
        viewModelOf(::CharacterDetailViewModel)
        viewModelOf(::DownloadsViewModel)
        viewModelOf(::FeedbackViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::LoginViewModel)
        viewModelOf(::MangaReaderViewModel)
        viewModelOf(::MangaViewModel)
        viewModelOf(::MediaDetailViewModel)
        viewModelOf(::NewsFeedViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::SeasonalCalendarViewModel)
        viewModelOf(::StatsViewModel)
        viewModelOf(::UserListsViewModel)
        viewModelOf(::VideoPlayerViewModel)
    }
