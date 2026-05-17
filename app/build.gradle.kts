import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry.android)
}

android {
    namespace = "ani.saikou"
    compileSdk = 35

    defaultConfig {
        applicationId = "ani.saikou.v2"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.2"

        vectorDrawables {
            useSupportLibrary = true
        }

        // OpenAI API key from local.properties
        val localProps = rootProject.file("local.properties")
        val props = Properties()
        if (localProps.exists()) {
            localProps.inputStream().use { props.load(it) }
        }
        val openAiKey =
            (props.getProperty("OPENAI_API_KEY") ?: "")
                .trim()
                .removeSurrounding("\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiKey\"")
        val discordFeedback =
            (props.getProperty("DISCORD_FEEDBACK_WEBHOOK") ?: "")
                .trim()
                .removeSurrounding("\"")
        buildConfigField("String", "DISCORD_FEEDBACK_WEBHOOK", "\"$discordFeedback\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs +=
            listOf(
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // No more composeOptions { kotlinCompilerExtensionVersion = ... } — the
    // org.jetbrains.kotlin.plugin.compose Gradle plugin (declared above) drives
    // the compiler now, version-locked to Kotlin.

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        // Robolectric needs the merged Android resources on the JVM test
        // classpath to instantiate its shadow Application. KoinGraphTest
        // depends on this for ApplicationProvider.getApplicationContext().
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Layered modules — domain currently owns the data models and the
    // AnilistRepository interface. Source migration into the other layers
    // (data, presentation, etc.) happens in subsequent commits.
    implementation(project(":domain"))
    implementation(project(":platform"))
    implementation(project(":platform-android"))
    implementation(project(":data"))
    implementation(project(":data-android"))
    implementation(project(":presentation"))
    implementation(project(":shared-ui"))

    // Koin — DI graph. koin-androidx-compose unlocks koinViewModel() in
    // composables, which the next commit migrates ViewModels onto.
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // PrettyLog — our extracted logging library (via JitPack)
    implementation(libs.prettylog)

    // Compose BOM — Dec 2024
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Image loading — Coil 3 splits the network fetcher into a separate
    // module; the OkHttp variant reuses the engine our Ktor stack already
    // pulls in transitively, so no extra OkHttp dep is needed.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ExoPlayer (video playback)
    val media3Version = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Network (Ktor — CMP-ready)
    val ktorVersion = "2.3.13"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion") // Android engine (swap to darwin for iOS)
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Accompanist (system UI, pager)
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")
    implementation("com.google.accompanist:accompanist-pager:0.36.0")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.36.0")

    // Room runtime + DAOs come transitively from :data-android (declared
    // there with `api` so :app sees DAO/entity types). The KSP processor
    // for Room only runs in the module that owns the @Dao/@Entity sources.

    // HTML parsing (for anime/manga source parsers)
    implementation("org.jsoup:jsoup:1.18.3")

    // Lottie animations
    implementation("com.airbnb.android:lottie-compose:6.6.2")

    // WorkManager (background episode checks)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.browser:browser:1.8.0")

    // Sentry — crash + non-fatal error reporting (DSN configured in AndroidManifest.xml)
    implementation("io.sentry:sentry-android:7.18.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Test — JVM-side unit tests (Koin graph validation, future use case
    // tests that need :app's bindings, etc.). Robolectric powers the
    // androidContext() shim that Koin needs at startup.
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

sentry {
    org.set("shidji-inc")
    projectName.set("android")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(true)
}
