plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ani.saikou.platform.android"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":platform"))

    // Sentry is the concrete error sink today. Keeping the integration here
    // means commonMain consumers (use cases, repos, parsers) can stay
    // Sentry-free and reach it only through the Logger interface.
    implementation(libs.sentry.android)

    // Koin module declaration uses the `module { }` DSL — no Android extensions
    // needed here since this module doesn't touch Activity/Application context.
    implementation(libs.koin.core)
}
