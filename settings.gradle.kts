pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Miyo"

// Existing application module — untouched for now. Source migration into
// the layered modules below happens feature-by-feature in subsequent commits.
include(":app")

// --- KMP modules (Android target today; iOS targets added later) ---
include(":domain")
include(":data")
include(":dto")
include(":platform")
include(":presentation")
include(":utils")

// --- Android-only modules ---
include(":data-android")
include(":platform-android")
include(":utils-android")
include(":ui")
include(":shared-ui")
