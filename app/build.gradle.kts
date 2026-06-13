import java.io.File
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    // Sentry Gradle plugin removed: 6.5–6.11.x all hit a
    // `setIgnoreExitValue(boolean)` API mismatch under Gradle 8.9, breaking
    // `:app:assembleRelease` at configure time even when every upload flag
    // is false. The runtime Sentry SDK is still in `dependencies` below, so
    // crash + breadcrumb reporting works — only source-context + ProGuard
    // mapping upload are sacrificed. Re-enable by re-adding `alias(libs.
    // plugins.sentry.android)` here + the `sentry { … }` block once either
    // the plugin ships a Gradle-8.9-compatible release or the wrapper is
    // downgraded to 8.8.
}

android {
    namespace = "ani.saikou"
    compileSdk = 35

    defaultConfig {
        applicationId = "ani.saikou.v2"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.3.1"

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

        // Self-update manifest URL. Overridable per-build via local.properties
        // (UPDATE_MANIFEST_URL=…) or via the environment, falling back to the
        // GitHub Pages default that the release workflow publishes. Keeping
        // it in BuildConfig means the feature module never hard-codes the URL.
        val updateManifestUrl =
            (
                System.getenv("UPDATE_MANIFEST_URL")
                    ?: props.getProperty("UPDATE_MANIFEST_URL")
                    ?: "https://theshid.github.io/miyo/update.json"
            ).trim()
                .removeSurrounding("\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
    }

    // Env-var driven release signing. Local debug builds are untouched —
    // they continue to sign with the SDK's default debug keystore.
    // CI fails with a clear message when the four secrets are missing;
    // the workflow surfaces that as a workflow failure.
    val ciKeystoreB64 = System.getenv("ANDROID_KEYSTORE_BASE64").orEmpty()
    val ciKeystorePass = System.getenv("ANDROID_KEYSTORE_PASSWORD").orEmpty()
    val ciKeyAlias = System.getenv("ANDROID_KEY_ALIAS").orEmpty()
    val ciKeyPass = System.getenv("ANDROID_KEY_PASSWORD").orEmpty()
    val ciSigningConfigured =
        ciKeystoreB64.isNotBlank() &&
            ciKeystorePass.isNotBlank() &&
            ciKeyAlias.isNotBlank() &&
            ciKeyPass.isNotBlank()

    if (ciSigningConfigured) {
        // Materialize the decoded keystore in the JVM's temp dir — NOT under
        // app/build/. Using build/ would let `./gradlew clean assembleRelease`
        // delete the file between configuration and the signing task,
        // breaking a clean release build.
        val keystoreFile: File = File.createTempFile("miyo-ci-keystore", ".jks")
        keystoreFile.deleteOnExit()
        // getMimeDecoder tolerates line breaks and surrounding whitespace —
        // `base64 -i keystore.jks` produces multi-line output, and GitHub
        // Actions can preserve those newlines in the secret. The strict
        // decoder would reject either case.
        keystoreFile.writeBytes(Base64.getMimeDecoder().decode(ciKeystoreB64))

        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = ciKeystorePass
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (ciSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// `sentry { … }` block removed alongside the Sentry Gradle plugin — see the
// rationale in the plugins block above.
