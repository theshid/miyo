package ani.saikou.data.di

import ani.saikou.data.source.manga.MangaDexParser
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Bindings for the KMP-portable parts of the data layer. Today that's just
 * MangaDexParser — its HttpClient + Logger come from sibling modules
 * (HttpClient from :data-android, Logger from :platform-android), all
 * merged into the same Koin graph at startup.
 *
 * MangaSourceRepositoryImpl's binding lives in :data-android even though
 * the impl class itself is in commonMain — the binding has to construct
 * MangaPillParser (JVM-only, lives in androidMain), and Koin module DSL
 * is line-by-line type-resolved so the binding-site has to see the
 * concrete androidMain type.
 *
 * Parsers are NOT bound to the `MangaSource` interface — both satisfy it
 * and the binding would be ambiguous. Consumers that need a specific
 * source inject the concrete type (e.g. `get<MangaDexParser>()`); the repo
 * accepts them as `MangaSource` via Kotlin's structural conformance.
 */
val dataModule =
    module {
        singleOf(::MangaDexParser)
    }
