plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    // iOS targets — uncommented when iOS support lands. KMP lifecycle +
    // koin already resolve for iosX64/iosArm64; the VMs themselves are
    // already commonMain, so no actual{} stubs are needed.
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Domain — VMs depend on repository / source interfaces, not impls.
            implementation(project(":domain"))

            // :platform exposes the Logger contract. VMs reach for it when
            // they need to emit telemetry directly (e.g. player error events
            // that aren't worth a use case wrapper).
            implementation(project(":platform"))

            // KMP-aware Lifecycle — gives commonMain access to ViewModel +
            // viewModelScope without pulling Android into common code.
            implementation(libs.lifecycle.viewmodel.kmp)
            implementation(libs.lifecycle.viewmodel.savedstate.kmp)

            // Koin — VMs are constructor-injected; commonMain only needs the
            // core to declare definitions. The Android-side bridge
            // (koin-android / koin-compose) lives where consumers do.
            implementation(libs.koin.core)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}

android {
    namespace = "ani.saikou.presentation"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Map AGP's "main" sourceSet to the KMP androidMain layout.
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
}
