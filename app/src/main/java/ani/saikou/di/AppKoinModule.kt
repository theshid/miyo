package ani.saikou.di

import ani.saikou.BuildConfig
import ani.saikou.data.android.di.IS_DEBUG_QUALIFIER
import ani.saikou.data.android.update.UpdateRepositoryImpl
import ani.saikou.data.local.ConnectivityObserver
import ani.saikou.data.local.OnboardingPrefs
import ani.saikou.data.local.TokenStorage
import ani.saikou.data.local.downloads.DownloadService
import ani.saikou.data.remote.AiChatServiceImpl
import ani.saikou.data.remote.AnilistApi
import ani.saikou.data.remote.FeedbackServiceImpl
import ani.saikou.data.remote.news.ANNNewsSource
import ani.saikou.data.remote.news.JikanNewsSource
import ani.saikou.data.remote.news.RedditNewsSource
import ani.saikou.data.remote.torrent.AniDexSource
import ani.saikou.data.remote.torrent.BTDiggSource
import ani.saikou.data.remote.torrent.NyaaSource
import ani.saikou.data.repository.AiChatRepositoryImpl
import ani.saikou.data.repository.AnilistRepositoryImpl
import ani.saikou.data.repository.AuthRepositoryImpl
import ani.saikou.data.repository.FeedbackRepositoryImpl
import ani.saikou.data.repository.NewsRepositoryImpl
import ani.saikou.data.repository.TorrentRepositoryImpl
import ani.saikou.domain.repository.AiChatRepository
import ani.saikou.domain.repository.AnilistRepository
import ani.saikou.domain.repository.AuthRepository
import ani.saikou.domain.repository.FeedbackRepository
import ani.saikou.domain.repository.NewsRepository
import ani.saikou.domain.repository.TorrentRepository
import ani.saikou.domain.repository.UpdateRepository
import ani.saikou.domain.source.AiChatService
import ani.saikou.domain.source.DownloadDispatcher
import ani.saikou.domain.source.FeedbackService
import ani.saikou.domain.source.NewsSource
import ani.saikou.domain.source.TorrentSource
import ani.saikou.domain.usecase.activity.ObserveActivityCalendarUseCase
import ani.saikou.domain.usecase.activity.RecordActivityEventUseCase
import ani.saikou.domain.usecase.ai.BuildAiUserContextSnippetUseCase
import ani.saikou.domain.usecase.ai.CatchMeUpUseCase
import ani.saikou.domain.usecase.ai.SendAiChatMessageUseCase
import ani.saikou.domain.usecase.anilist.DeleteAnilistListEntryUseCase
import ani.saikou.domain.usecase.anilist.EditListEntryUseCase
import ani.saikou.domain.usecase.anilist.GetAiringRangeUseCase
import ani.saikou.domain.usecase.anilist.GetAnimeDiscoveryUseCase
import ani.saikou.domain.usecase.anilist.GetCharacterUseCase
import ani.saikou.domain.usecase.anilist.GetHomeAnilistSnapshotUseCase
import ani.saikou.domain.usecase.anilist.GetMangaDiscoveryUseCase
import ani.saikou.domain.usecase.anilist.GetMediaDetailUseCase
import ani.saikou.domain.usecase.anilist.GetSeasonalAnimeUseCase
import ani.saikou.domain.usecase.anilist.GetSignedInUserUseCase
import ani.saikou.domain.usecase.anilist.GetUserAnimeListUseCase
import ani.saikou.domain.usecase.anilist.GetUserFavoritesUseCase
import ani.saikou.domain.usecase.anilist.GetUserMangaListUseCase
import ani.saikou.domain.usecase.anilist.GetUserStatsUseCase
import ani.saikou.domain.usecase.anilist.LoadMorePopularAnimeUseCase
import ani.saikou.domain.usecase.anilist.LoadMorePopularMangaUseCase
import ani.saikou.domain.usecase.anilist.RefreshHomeAnilistSnapshotUseCase
import ani.saikou.domain.usecase.anilist.ResolveChapterCountUseCase
import ani.saikou.domain.usecase.anilist.SearchMediaUseCase
import ani.saikou.domain.usecase.anilist.ToggleFavoriteMediaUseCase
import ani.saikou.domain.usecase.anime.GetEpisodeSkipTimesUseCase
import ani.saikou.domain.usecase.anime.LoadEpisodeStreamUseCase
import ani.saikou.domain.usecase.anime.ResolveAnimeSourcesUseCase
import ani.saikou.domain.usecase.auth.GetAnilistAuthUrlUseCase
import ani.saikou.domain.usecase.downloads.CancelChapterByNumberUseCase
import ani.saikou.domain.usecase.downloads.CancelChapterDownloadUseCase
import ani.saikou.domain.usecase.downloads.CleanupPhantomDownloadsUseCase
import ani.saikou.domain.usecase.downloads.DeleteAllDownloadsForMangaUseCase
import ani.saikou.domain.usecase.downloads.EstimateNextChaptersBytesUseCase
import ani.saikou.domain.usecase.downloads.EvictReadChaptersUseCase
import ani.saikou.domain.usecase.downloads.GetCompletedChapterUseCase
import ani.saikou.domain.usecase.downloads.GetLocalPagesUseCase
import ani.saikou.domain.usecase.downloads.ObserveChapterDownloadsForMangaUseCase
import ani.saikou.domain.usecase.downloads.ObserveDownloadsUseCase
import ani.saikou.domain.usecase.downloads.PauseChapterDownloadUseCase
import ani.saikou.domain.usecase.downloads.QueueChapterDownloadUseCase
import ani.saikou.domain.usecase.downloads.QueueNextChaptersUseCase
import ani.saikou.domain.usecase.feedback.SubmitFeedbackUseCase
import ani.saikou.domain.usecase.history.GetReadingHistoryForMediaUseCase
import ani.saikou.domain.usecase.history.GetWatchHistoryForMediaUseCase
import ani.saikou.domain.usecase.history.ObserveChaptersReadCountUseCase
import ani.saikou.domain.usecase.history.ObserveContinueWatchingUseCase
import ani.saikou.domain.usecase.history.ObserveEpisodesWatchedCountUseCase
import ani.saikou.domain.usecase.history.ObserveReadingHistoryUseCase
import ani.saikou.domain.usecase.history.ObserveRecentWatchHistoryUseCase
import ani.saikou.domain.usecase.history.UpsertReadingHistoryUseCase
import ani.saikou.domain.usecase.history.UpsertWatchHistoryUseCase
import ani.saikou.domain.usecase.manga.GetChapterPagesUseCase
import ani.saikou.domain.usecase.manga.GetChaptersForSourceUseCase
import ani.saikou.domain.usecase.manga.ResolveChaptersForMangaUseCase
import ani.saikou.domain.usecase.manga.ResolveMangaSourcesUseCase
import ani.saikou.domain.usecase.news.GetAiringScheduleUseCase
import ani.saikou.domain.usecase.news.GetLatestNewsUseCase
import ani.saikou.domain.usecase.torrents.SearchTorrentsUseCase
import ani.saikou.domain.usecase.update.CheckForUpdateUseCase
import ani.saikou.domain.usecase.update.CleanupUpdateArtifactsUseCase
import ani.saikou.domain.usecase.update.DownloadUpdateUseCase
import ani.saikou.domain.usecase.update.InstallUpdateUseCase
import ani.saikou.presentation.screens.ai.AiChatViewModel
import ani.saikou.presentation.screens.anime.AnimeViewModel
import ani.saikou.presentation.screens.character.CharacterDetailViewModel
import ani.saikou.presentation.screens.detail.MediaDetailViewModel
import ani.saikou.presentation.screens.downloads.DownloadsViewModel
import ani.saikou.presentation.screens.feedback.FeedbackViewModel
import ani.saikou.presentation.screens.home.HomeViewModel
import ani.saikou.presentation.screens.lists.UserListsViewModel
import ani.saikou.presentation.screens.login.LoginViewModel
import ani.saikou.presentation.screens.manga.MangaViewModel
import ani.saikou.presentation.screens.news.NewsFeedViewModel
import ani.saikou.presentation.screens.player.VideoPlayerViewModel
import ani.saikou.presentation.screens.reader.MangaReaderViewModel
import ani.saikou.presentation.screens.search.SearchViewModel
import ani.saikou.presentation.screens.seasonal.SeasonalCalendarViewModel
import ani.saikou.presentation.screens.stats.StatsViewModel
import ani.saikou.presentation.screens.torrent.TorrentSearchViewModel
import ani.saikou.presentation.screens.update.UpdateViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
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
        // DownloadService is a foreground Service that pulls MainActivity +
        // R from :app, so it can't move down to :data-android. The fun
        // interface keeps that Android-y wiring out of :shared-ui screens.
        single<DownloadDispatcher> {
            val ctx = androidContext()
            DownloadDispatcher { DownloadService.start(ctx) }
        }

        // ─── Remote services ───────────────────────────────────────────────
        single<AiChatService> { AiChatServiceImpl(apiKey = BuildConfig.OPENAI_API_KEY) }
        single<AiChatRepository> { AiChatRepositoryImpl(service = get(), anilistRepository = get()) }
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

        // ─── Torrent sources + repository ────────────────────────────────
        // Three trackers fan out into a single result list. No dedup —
        // rows from different trackers are intentionally distinct (different
        // magnets, seeder counts), and the screen's source-filter chip
        // relies on per-source attribution.
        single { NyaaSource() }
        single { BTDiggSource() }
        single { AniDexSource() }
        single<TorrentRepository> {
            TorrentRepositoryImpl(
                sources = listOf<TorrentSource>(get<NyaaSource>(), get<BTDiggSource>(), get<AniDexSource>()),
            )
        }

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

        // ─── Self-update ──────────────────────────────────────────────────
        // UpdateRepositoryImpl pulls the live HttpClient + Logger from the
        // graph and reads BuildConfig values directly so the URL, applicationId,
        // and versionCode/Name are configured in one place (app/build.gradle.kts).
        single<UpdateRepository> {
            UpdateRepositoryImpl(
                context = androidContext(),
                client = get(),
                manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
                fileProviderAuthority = "${androidContext().packageName}.fileprovider",
                expectedPackage = androidContext().packageName,
                installedVersionCode = BuildConfig.VERSION_CODE,
                installedVersionName = BuildConfig.VERSION_NAME,
                logger = get(),
            )
        }

        // ─── Use cases ─────────────────────────────────────────────────────
        // factoryOf — fresh instance per resolution. Use cases are stateless
        // wrappers and don't benefit from singleton-ness; per-call alloc keeps
        // the door open for parameterized state if a future use case needs it.
        factoryOf(::BuildAiUserContextSnippetUseCase)
        factoryOf(::CancelChapterByNumberUseCase)
        factoryOf(::CancelChapterDownloadUseCase)
        factoryOf(::CheckForUpdateUseCase)
        factoryOf(::CleanupPhantomDownloadsUseCase)
        factoryOf(::CleanupUpdateArtifactsUseCase)
        factoryOf(::CatchMeUpUseCase)
        factoryOf(::DeleteAllDownloadsForMangaUseCase)
        factoryOf(::DeleteAnilistListEntryUseCase)
        factoryOf(::DownloadUpdateUseCase)
        factoryOf(::EditListEntryUseCase)
        factoryOf(::EstimateNextChaptersBytesUseCase)
        factoryOf(::EvictReadChaptersUseCase)
        factoryOf(::GetAiringRangeUseCase)
        factoryOf(::GetAiringScheduleUseCase)
        factoryOf(::GetAnilistAuthUrlUseCase)
        factoryOf(::GetAnimeDiscoveryUseCase)
        factoryOf(::GetChapterPagesUseCase)
        factoryOf(::GetChaptersForSourceUseCase)
        factoryOf(::GetCharacterUseCase)
        factoryOf(::GetCompletedChapterUseCase)
        factoryOf(::GetEpisodeSkipTimesUseCase)
        factoryOf(::GetHomeAnilistSnapshotUseCase)
        factoryOf(::GetLatestNewsUseCase)
        factoryOf(::GetLocalPagesUseCase)
        factoryOf(::GetMangaDiscoveryUseCase)
        factoryOf(::GetMediaDetailUseCase)
        factoryOf(::GetReadingHistoryForMediaUseCase)
        factoryOf(::GetSeasonalAnimeUseCase)
        factoryOf(::GetSignedInUserUseCase)
        factoryOf(::GetUserAnimeListUseCase)
        factoryOf(::GetUserFavoritesUseCase)
        factoryOf(::GetUserMangaListUseCase)
        factoryOf(::GetUserStatsUseCase)
        factoryOf(::GetWatchHistoryForMediaUseCase)
        factoryOf(::InstallUpdateUseCase)
        factoryOf(::LoadEpisodeStreamUseCase)
        factoryOf(::LoadMorePopularAnimeUseCase)
        factoryOf(::LoadMorePopularMangaUseCase)
        factoryOf(::ObserveActivityCalendarUseCase)
        factoryOf(::ObserveChapterDownloadsForMangaUseCase)
        factoryOf(::ObserveChaptersReadCountUseCase)
        factoryOf(::ObserveContinueWatchingUseCase)
        factoryOf(::ObserveDownloadsUseCase)
        factoryOf(::ObserveEpisodesWatchedCountUseCase)
        factoryOf(::ObserveReadingHistoryUseCase)
        factoryOf(::ObserveRecentWatchHistoryUseCase)
        factoryOf(::PauseChapterDownloadUseCase)
        factoryOf(::QueueChapterDownloadUseCase)
        factoryOf(::QueueNextChaptersUseCase)
        factoryOf(::RecordActivityEventUseCase)
        factoryOf(::RefreshHomeAnilistSnapshotUseCase)
        factoryOf(::ResolveAnimeSourcesUseCase)
        factoryOf(::ResolveChapterCountUseCase)
        factoryOf(::ResolveChaptersForMangaUseCase)
        factoryOf(::ResolveMangaSourcesUseCase)
        factoryOf(::SearchMediaUseCase)
        factoryOf(::SearchTorrentsUseCase)
        factoryOf(::SendAiChatMessageUseCase)
        factoryOf(::SubmitFeedbackUseCase)
        factoryOf(::ToggleFavoriteMediaUseCase)
        factoryOf(::UpsertReadingHistoryUseCase)
        factoryOf(::UpsertWatchHistoryUseCase)

        // ─── ViewModels ────────────────────────────────────────────────────
        // Every Compose-backed ViewModel resolves through Koin now.
        // viewModelOf reflects against the constructor and pulls each param
        // from the graph (including SavedStateHandle for VMs that take it).
        viewModelOf(::AiChatViewModel)
        viewModelOf(::AnimeViewModel)
        viewModelOf(::CharacterDetailViewModel)
        viewModelOf(::DownloadsViewModel)
        viewModelOf(::FeedbackViewModel)
        viewModel {
            HomeViewModel(
                getAnilistSnapshot = get(),
                refreshAnilistSnapshot = get(),
                observeReadingHistory = get(),
                observeContinueWatching = get(),
                observeRecentWatchHistory = get(),
                observeEpisodesWatchedCount = get(),
                observeChaptersReadCount = get(),
                observeActivityCalendar = get(),
                appVersionName = BuildConfig.VERSION_NAME,
            )
        }
        viewModelOf(::LoginViewModel)
        viewModelOf(::MangaReaderViewModel)
        viewModelOf(::MangaViewModel)
        viewModelOf(::MediaDetailViewModel)
        viewModelOf(::NewsFeedViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::SeasonalCalendarViewModel)
        viewModelOf(::StatsViewModel)
        viewModelOf(::TorrentSearchViewModel)
        viewModelOf(::UpdateViewModel)
        viewModelOf(::UserListsViewModel)
        viewModelOf(::VideoPlayerViewModel)
    }
