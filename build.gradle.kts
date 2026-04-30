// Plugins are declared here with `apply false` so submodules can apply them
// without redeclaring the version. Versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.sentry.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Apply ktlint + detekt to every module without per-module duplication.
// Both run on `./gradlew check`; format with `./gradlew ktlintFormat`
// or `./gradlew detekt --auto-correct`.
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension>("ktlint") {
        // Disable the unsigned ktlint version-check that hits the network on
        // every invocation — slows local builds and breaks offline use.
        version.set("1.4.1")
        android.set(true)
        ignoreFailures.set(false)
        // Build/ are generated, schemas are KSP/Room output. None should ever
        // be linted.
        filter {
            exclude { it.file.path.contains("/build/") }
            exclude { it.file.path.contains("/generated/") }
        }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension>("detekt") {
        toolVersion = rootProject.libs.versions.detekt.get()
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        // Don't bail out the whole build on the first finding during local
        // dev — `--build-upon-default-config` keeps detekt running even when
        // some rules trip. CI will set `ignoreFailures = false` later.
        ignoreFailures = false
        autoCorrect = true
        parallel = true
        buildUponDefaultConfig = true
        // Source set discovery — detekt needs to know which Kotlin source
        // dirs to scan. Both KMP and Android-only modules expose `src/main`
        // or `src/{commonMain,androidMain}` shapes; the plugin auto-detects.
    }

    dependencies {
        // The formatting rule pack (ktlint inside detekt). Shared via a
        // separate detektPlugins configuration — `add` is needed because
        // the configuration is created lazily by the plugin.
        add("detektPlugins", rootProject.libs.detekt.formatting)
    }
}
