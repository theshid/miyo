plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ani.saikou.ui"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Anchor on the same Compose BOM the app uses so versions don't drift.
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    // Compose runtime is required for any Compose-enabled module — without
    // it, the compiler plugin can't resolve @Composable / `remember` / etc.
    implementation("androidx.compose.runtime:runtime")
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
