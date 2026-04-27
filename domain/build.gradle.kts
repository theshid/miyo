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

    // iOS targets — uncommented when iOS support lands:
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Repository interfaces expose Flow types — make coroutines part
            // of :domain's public surface so consumers see Flow without
            // adding their own dep.
            api(libs.kotlinx.coroutines.core)
        }

        // androidUnitTest hosts JVM-side use case unit tests. MockK lives
        // here (JVM-only) — KMP-friendly mocking is a future concern when
        // iOS targets land.
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }
    }
}

android {
    namespace = "ani.saikou.domain"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
