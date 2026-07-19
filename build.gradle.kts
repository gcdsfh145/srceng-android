plugins {
    id("com.android.application") version "9.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

// CI populates lib/, while the local source-engine build exports Android
// libraries one directory above this project. Prefer the packaged build output
// when it exists and keep the fallback automatic for local APK builds.
val nativeLibRoot = if (file("lib").isDirectory) "lib" else "../android_build/lib"

android {
    namespace = "com.valvesoftware.source"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.valvesoftware.source"
        minSdk = 24
        // Match the original launcher so Android shows the legacy
        // WRITE_EXTERNAL_STORAGE runtime permission dialog.
        targetSdk = 36
        versionCode = 1
        versionName = "1.18"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.directories += "src"
            kotlin.directories += "src"
            res.directories += "res"
            assets.directories += "assets"
            jniLibs.directories += nativeLibRoot
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources.excludes += setOf(
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES"
        )
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui:1.10.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.0")
}
