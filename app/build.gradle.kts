/**
 * app/build.gradle.kts — the module-level build script for the app module.
 *
 * GRADLE BUILD SYSTEM
 * ===================
 * Android projects use Gradle as their build system. Gradle is a JVM-based
 * build tool that uses a Groovy or Kotlin DSL to describe how to compile,
 * package, and deploy an Android app.
 *
 * The project has two build files:
 *   - build.gradle.kts (at the project root): configures the overall project,
 *     plugin versions, and repository declarations.
 *   - app/build.gradle.kts (this file): configures the :app module — what to
 *     compile, what SDK versions to target, and which libraries to include.
 *
 * Kotlin DSL (.kts extension) uses Kotlin syntax for build scripts instead of
 * Groovy. It provides type safety and IDE completion for build configuration.
 *
 * PLUGINS
 * =======
 * Plugins extend Gradle with new tasks and configuration blocks. The `alias()`
 * syntax references plugin IDs from libs.versions.toml (see that file for the
 * version declarations):
 *
 *   android.application   — the Android Gradle Plugin (AGP). Adds the `android`
 *                           configuration block and standard Android build tasks
 *                           (assembleDebug, assembleRelease, installDebug, etc.).
 *
 *   kotlin.android        — Kotlin support for Android. Enables compiling .kt
 *                           files and interoperability with Java Android APIs.
 *
 *   kotlin.compose        — the Compose compiler plugin. This is separate from
 *                           the Kotlin plugin since Compose 1.5 / Kotlin 2.0.
 *                           It transforms @Composable functions during compilation.
 *
 *   ksp                   — Kotlin Symbol Processing. A modern annotation
 *                           processor used by Room to generate DAO implementation
 *                           code at compile time (replaces the older kapt tool).
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    /**
     * NAMESPACE
     * =========
     * The unique identifier for this module's generated R class and BuildConfig.
     * This replaces the deprecated `package` attribute in AndroidManifest.xml
     * for resource references. Must match the package name used in Kotlin sources.
     */
    namespace = "com.degel.beepfinder"

    /**
     * SDK VERSIONS
     * ============
     * compileSdk: the Android SDK version used to compile the app. The app can
     *   use any API available in this version. Set to the latest stable SDK.
     *   This does NOT determine what Android versions can run the app.
     *
     * minSdk: the minimum Android API level the app supports. Devices running
     *   older Android versions cannot install the app. API 29 = Android 10,
     *   which gives us modern notification channel APIs without a very old
     *   device cutoff.
     *
     * targetSdk: tells Android which version's behavior the app expects. Android
     *   applies backward-compat quirks below this version but uses stricter
     *   modern behavior at or above it. Should match compileSdk for new apps.
     *
     * versionCode: integer version used by the Play Store to determine update
     *   order. Must increase with each published release.
     *
     * versionName: human-readable version string shown to users ("1.0", "2.1.3").
     */
    compileSdk = 35

    defaultConfig {
        applicationId = "com.degel.beepfinder"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    /**
     * BUILD TYPES
     * ===========
     * Android supports multiple build variants. The two default types are:
     *
     *   debug:   automatically configured by AGP. Includes debug symbols,
     *            the .debug suffix in the app ID (to coexist with release),
     *            and is signed with a debug keystore. No configuration needed.
     *
     *   release: the published variant. isMinifyEnabled=true would enable
     *            R8 (code shrinker/obfuscator). We leave it false for now
     *            (personal app, no need to shrink). For Play Store publication,
     *            enable minification and add a proguard-rules.pro file.
     */
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    /**
     * COMPILE OPTIONS
     * ===============
     * sourceCompatibility / targetCompatibility: the Java bytecode version used
     *   for compiling any Java sources. Java 17 aligns with the LTS release
     *   and is required by some Jetpack libraries.
     *
     * kotlin { jvmToolchain(17) }: tells the Kotlin compiler to target JVM 17
     *   bytecode. Replaces the deprecated `kotlinOptions { jvmTarget = "17" }`
     *   approach. The toolchain also selects the correct JDK version for
     *   compilation if multiple are available.
     */
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    /**
     * BUILD FEATURES
     * ==============
     * compose = true: enables the Jetpack Compose UI toolkit. This activates
     *   the Compose compiler plugin (declared above) and allows importing
     *   Compose libraries. Without this, @Composable annotations are unknown.
     *
     * Other optional features (not used here):
     *   buildConfig = true  — generates a BuildConfig class with app constants
     *   viewBinding = true  — generates binding classes for XML layouts
     *   dataBinding = true  — XML data binding (older approach than Compose)
     */
    buildFeatures {
        compose = true
    }
}

/**
 * DEPENDENCIES
 * ============
 * All library dependencies are declared here. Version numbers are managed
 * centrally in gradle/libs.versions.toml (the "version catalog"), and
 * referenced via the `libs.` accessor. This avoids duplicating version
 * strings across multiple build files in a multi-module project.
 *
 * DEPENDENCY CONFIGURATIONS
 * ==========================
 *   implementation: the dependency is compiled into the app and available
 *     at runtime. Not exposed to other modules (appropriate for an app module
 *     which has no consumers).
 *
 *   debugImplementation: included only in the debug build. The Compose UI
 *     tooling library (layout inspector, preview) is only needed during
 *     development.
 *
 *   ksp: not a runtime dependency — tells KSP (Kotlin Symbol Processor) to
 *     run this annotation processor during compilation. Room uses it to
 *     generate type-safe DAO implementations from the @Dao interfaces.
 *
 * COMPOSE BOM
 * ===========
 * `platform(libs.compose.bom)` is a Bill of Materials (BOM) — a special
 * dependency that sets the versions of all Compose libraries in one place.
 * After importing the BOM, individual Compose dependencies (compose.ui,
 * compose.material3, etc.) don't need explicit version numbers — they're
 * managed by the BOM. This guarantees all Compose libraries are compatible
 * with each other.
 *
 * LIBRARY BREAKDOWN
 * =================
 *
 * AndroidX Core:
 *   core-ktx               — Kotlin extensions for common Android APIs.
 *                            Adds concise extension functions like
 *                            `context.getSystemService<PowerManager>()`.
 *
 * Lifecycle:
 *   lifecycle-runtime-ktx  — Lifecycle-aware coroutine scopes and extensions.
 *   lifecycle-viewmodel-compose — viewModel() composable function for
 *                            retrieving ViewModels from Compose.
 *
 * Activity:
 *   activity-compose       — ComponentActivity.setContent {} and
 *                            enableEdgeToEdge().
 *
 * Compose UI:
 *   compose-bom            — BOM for version alignment.
 *   compose.ui             — core Compose runtime and layout.
 *   compose.ui.tooling.preview — @Preview annotation support.
 *   compose.material3      — Material 3 components (TopAppBar, Button, etc.).
 *   compose.material.icons — extended Material icon set (Settings gear,
 *                            ArrowBack, etc.). This is a large library;
 *                            R8/minification would strip unused icons in
 *                            a release build.
 *   compose.ui.tooling     — debug-only: layout inspector, slow composition
 *                            tracking. Never include in release builds.
 *
 * Room:
 *   room-runtime           — Room database runtime (annotations, core classes).
 *   room-ktx               — Kotlin extensions: Flow support for queries,
 *                            suspend function support for DAO methods.
 *   room-compiler (ksp)    — annotation processor that generates the
 *                            database implementation code at compile time.
 *
 * Coroutines:
 *   kotlinx-coroutines-android — Android-specific coroutines integration:
 *                            Dispatchers.Main targeting Android's main looper.
 */
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
}
