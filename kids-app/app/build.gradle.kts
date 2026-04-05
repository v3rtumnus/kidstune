plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.screenshot)
}

android {
    namespace   = "at.kidstune.kids"
    compileSdk  = 35

    defaultConfig {
        applicationId = "at.kidstune.kids"
        minSdk        = 28
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"https://kidstune.altenburger.io\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Screenshot Testing – reference images are stored in src/debug/screenshotTest/reference/
    // Run: ./gradlew updateDebugScreenshotTest   → generate/update PNGs
    // Run: ./gradlew validateDebugScreenshotTest → fail if visuals changed
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    // ── Compose BOM ──────────────────────────────────────────────────────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // ── AndroidX ─────────────────────────────────────────────────────────────
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Room ─────────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Coil 3 ───────────────────────────────────────────────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ── Ktor ─────────────────────────────────────────────────────────────────
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.neg)
    implementation(libs.ktor.serialization.json)

    // ── WorkManager ───────────────────────────────────────────────────────────
    implementation(libs.work.runtime.ktx)

    // ── Security ─────────────────────────────────────────────────────────────
    implementation(libs.security.crypto)

    // ── Serialization ────────────────────────────────────────────────────────
    implementation(libs.serialization.json)

    // ── Spotify App Remote SDK ────────────────────────────────────────────────
    // IMPORTANT: Download the SDK manually from https://developer.spotify.com/documentation/android/
    // and place the AAR at kids-app/libs/spotify-app-remote-release-0.8.0.aar
    // Then REMOVE kids-app/app/src/main/java/com/spotify/SpotifyStubs.kt (the compile-time stub).
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // ── Unit tests ───────────────────────────────────────────────────────────
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit4.vintage.engine) // enables @RunWith(RobolectricTestRunner) under JUnit Platform
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    releaseImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.work.testing)

    // ── Screenshot tests ─────────────────────────────────────────────────────
    screenshotTestImplementation(composeBom)
    screenshotTestImplementation(libs.compose.ui.tooling)
}

// Use JUnit 5 engine for unit tests
tasks.withType<Test> {
    useJUnitPlatform()
}
