plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Version comes from git, so every build is identifiable:
// at a tag v0.2.0 → "0.2.0"; three commits later → "0.2.0-3-gabc1234"; uncommitted changes add "-dirty".
// versionCode is the commit count on HEAD, so each new commit installs as an upgrade.
val gitVersionName: String = providers.exec {
    commandLine("git", "describe", "--tags", "--match", "v[0-9]*", "--always", "--dirty")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().removePrefix("v").ifBlank { "0.0.0-unknown" }

val gitVersionCode: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1

android {
    namespace = "io.github.murrayb52.sleeptight"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.murrayb52.sleeptight"
        minSdk = 28
        targetSdk = 37
        versionCode = gitVersionCode
        versionName = gitVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Exported Room schemas, so MigrationTestHelper can build old database versions in tests.
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    // Third-party licence texts (e.g. the font's OFL) ship inside the app, from the repo's licences/ folder.
    sourceSets.getByName("main").assets.directories.add("$rootDir/licences")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":metrics"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    // Needed for the score cache and JSON export; added now because room-testing needs a newer
    // serialization runtime than Room pulls in, and the test classpath is pinned to the app's.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
