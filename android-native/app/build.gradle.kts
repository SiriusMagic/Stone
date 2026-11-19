plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.zirakusa.stone.nativeapp"
    compileSdk = 36
    defaultConfig {
        // Preview can coexist with Stone 1.3.2; never overwrite its user data.
        applicationId = "com.zirakusa.stone.kotlin.preview"
        minSdk = 26
        targetSdk = 35
        versionCode = 52
        versionName = "1.7.18"
        // Public relay that gives each phone a fixed HTTPS link (see ../relay). Empty = local only.
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    bundle {
        // Stone selects its locale at runtime in attachBaseContext. Keep all
        // translations in the APK instead of relying on a Play language split.
        language { enableSplit = false }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("roborazzi.test.record", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildTypes {
        debug {
            // Diagnostic-only A/B switch. Release and preview builds never expose it.
            buildConfigField("boolean", "AUDIO_DIAGNOSTICS_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            buildConfigField("boolean", "AUDIO_DIAGNOSTICS_ENABLED", "false")
        }
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("boolean", "AUDIO_DIAGNOSTICS_ENABLED", "false")
        }
    }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-session:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // WebSocket to the Stone relay (fixed public link for the MCP server).
    constraints {
        // Media3 brings Guava 33.3.1, affected by GHSA-xxph-c9ww-hj94 (CVE-2026-102554); 33.7.2 is the fixed release.
        implementation("com.google.guava:guava:33.7.2-android") { because("GHSA-xxph-c9ww-hj94") }
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.79.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.79.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }
