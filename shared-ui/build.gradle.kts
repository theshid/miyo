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
        }

        androidMain.dependencies {
            // ExoPlayer + AndroidView host live here — splash plays an MP4
            // via media3, which is Android-only. iOS will get an actual{}
            // impl using AVPlayer when targets are enabled.
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.media3.common)

            // SaikouTheme calls WindowCompat to control system bar contrast.
            // Pulled by SaikouTheme only — Android-only, so androidMain.
            implementation(libs.core.ktx)
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
    // Tooling-time previews (debug only — don't ship).
    debugImplementation(libs.compose.ui.tooling)
}

// Generated `Res` accessor must be public so consumer modules (e.g. :app's
// VideoPlayerScreen, which transitionally references the splash MP4 until
// the player migrates here) can `import miyo.shared_ui.generated.resources.Res`.
compose.resources {
    publicResClass = true
}
