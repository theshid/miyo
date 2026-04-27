plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
            implementation(project(":platform"))

            // Ktor — KMP HTTP client. Engine is supplied per platform (OkHttp
            // on Android, future Darwin/CIO on iOS). These are `api` so the
            // HttpClient binding in :data-android (which builds the client
            // with plugins) sees the Logging/ContentNegotiation/JSON types.
            api(libs.ktor.client.core)
            api(libs.ktor.client.content.negotiation)
            api(libs.ktor.serialization.kotlinx.json)
            api(libs.ktor.client.logging)
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }

        // Android-only sources — anything that depends on JVM-exclusive
        // libraries (Jsoup, java.net, Android APIs) lives here.
        val androidMain by getting {
            dependencies {
                // OkHttp engine for Ktor — battle-tested on Android. `api`
                // because :data-android needs to reference the OkHttp factory
                // when building the HttpClient via Koin.
                api(libs.ktor.client.okhttp)
                // Jsoup powers MangaPillParser's HTML scraping. JVM-only;
                // when iOS support lands we'll either drop MangaPill or
                // migrate to a KMP HTML parser (ksoup).
                implementation(libs.jsoup)
            }
        }
    }
}

android {
    namespace = "ani.saikou.data"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
