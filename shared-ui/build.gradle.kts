plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    // iOS targets — uncommented when iOS support lands. KMP-aware Compose
    // dependencies below already resolve for iosX64/iosArm64 via CMP.
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform — the plugin's Compose runtime/foundation/
            // material3, plus the `components-resources` accessor that
            // generates the `Res` object from src/commonMain/composeResources/.
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.ui)
            // `api` so consumers (e.g. :app) can `import org.jetbrains.compose.
            // resources.*` and use `Res` directly without re-declaring the dep.
            api(libs.compose.multiplatform.components.resources)

            // Screens reference VM types from :presentation and domain types
            // (e.g. FeedbackService.Category) from :domain. Both are KMP, so
            // commonMain dep here.
            implementation(project(":domain"))
            implementation(project(":presentation"))
        }

        androidMain.dependencies {
            // :data-android exposes Android-only utilities (e.g. OnboardingPrefs,
            // backed by SharedPreferences) that androidMain screens reach via
            // koinInject. The KMP-portable contracts already live in :domain.
            implementation(project(":data-android"))

            // ExoPlayer + AndroidView host live here — splash plays an MP4
            // via media3, which is Android-only. iOS will get an actual{}
            // impl using AVPlayer when targets are enabled.
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.media3.common)

            // SaikouTheme calls WindowCompat to control system bar contrast.
            // Pulled by SaikouTheme only — Android-only, so androidMain.
            implementation(libs.core.ktx)

            // LoginScreen kicks off OAuth via Custom Tabs — Android-only.
            implementation(libs.browser)

            // Material icons — Chat / Code / Send used by LoginScreen's social
            // bar. material-icons-extended is Android-only; CMP equivalent
            // ships with org.jetbrains.compose.material when iOS lands.
            // BOM pins the version in lockstep with :app's Compose libs.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.material.icons.extended)

            // koinViewModel() lookup. KMP equivalent (koin-compose-viewmodel)
            // gets pulled in commonMain when screens move there.
            implementation(libs.koin.androidx.compose)

            // Coil 3 — AsyncImage in NewsFeedScreen et al. Same versions
            // as :app to stay in lockstep.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.okhttp)

            // PrettyLog — used by VideoPlayerScreen for ExoPlayer-side
            // diagnostic logs (subtitle resolution, playback errors).
            // Screens-resident concern, not a VM/use-case one.
            implementation(libs.prettylog)

            // Lottie-Compose — VideoLoader / CatLoader animations.
            // Android-only (Lottie's Compose binding is JVM/Android).
            implementation(libs.lottie.compose)
        }
    }

    sourceSets.all {
        languageSettings.optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
    }
}

android {
    namespace = "ani.saikou.sharedui"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }

    // Map AGP's "main" sourceSet to the KMP androidMain layout — same
    // pattern Fido uses. Without this remap, AGP looks for src/main/
    // and skips src/androidMain/.
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
}

dependencies {
    // The @Preview annotation itself is referenced in androidMain source, so it
    // must be on the classpath for every variant — `implementation`, not debug-only.
    // The interactive renderer (ui-tooling) is still debug-only.
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}

// Generated `Res` accessor must be public so consumer modules (e.g. :app's
// VideoPlayerScreen, which transitionally references the splash MP4 until
// the player migrates here) can `import miyo.shared_ui.generated.resources.Res`.
compose.resources {
    publicResClass = true
}
