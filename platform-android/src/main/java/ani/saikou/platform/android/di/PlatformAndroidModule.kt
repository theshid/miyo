package ani.saikou.platform.android.di

import ani.saikou.platform.android.log.SentryLogger
import ani.saikou.platform.log.Logger
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin bindings for the Android implementations of [Logger] and any future
 * device-service abstractions declared in :platform. Consumed by the app's
 * `startKoin { modules(...) }` block.
 */
val platformAndroidModule =
    module {
        singleOf(::SentryLogger) bind Logger::class
    }
