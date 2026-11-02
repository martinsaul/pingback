plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.saul.pingback"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.saul.pingback"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // The release APK is built unsigned. Sign it with your own key (see docs/BUILDING.md).
            isMinifyEnabled = true
            isShrinkResources = true
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
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")

    // Documentation screenshots, rendered on the JVM (see docs/BUILDING.md).
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
}

// `./gradlew recordScreenshots` re-renders the PNGs in docs/screenshots. A normal test run skips them.
val screenshotDir = rootProject.layout.projectDirectory.dir("docs/screenshots").asFile.absolutePath
tasks.withType<Test>().configureEach {
    // Not project.hasProperty(): the recordScreenshots task itself would make that always true.
    if (providers.gradleProperty("recordScreenshots").isPresent) {
        systemProperty("pingback.screenshotDir", screenshotDir)
        systemProperty("roborazzi.test.record", "true")
    } else {
        // Rendering is slow and only matters for the docs, so keep it out of normal test runs.
        exclude("**/ScreenshotTest*")
    }
}
tasks.register<Exec>("recordScreenshots") {
    group = "documentation"
    description = "Renders the README screenshots into docs/screenshots."
    workingDir = rootProject.projectDir
    commandLine(
        "./gradlew", "--no-daemon", ":app:testDebugUnitTest",
        "--tests", "io.saul.pingback.ui.ScreenshotTest", "-PrecordScreenshots", "--rerun",
    )
}
