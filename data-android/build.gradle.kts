plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ani.saikou.data.android"
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
    // `api` for everything :app needs to see through this module — DAO types,
    // domain models, and the manga parsers are all referenced directly from
    // AppModule and ViewModels still living in :app.
    api(project(":domain"))
    api(project(":data"))
    api(project(":platform"))

    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)

    // PrettyLog — used by MangaDownloadManager for per-page failure breadcrumbs.
    implementation(libs.prettylog)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Koin — koin-android brings androidContext() for the SaikouDatabase
    // binding, which needs an Application context to instantiate.
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
