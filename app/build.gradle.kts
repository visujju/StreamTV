plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.streamtv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.streamtv.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
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
    freeCompilerArgs += "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api"
}

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Internal Modules
    implementation(project(":core:model"))
    implementation(project(":core:player"))
    implementation(project(":core:network"))
    implementation(project(":providers:api"))
    implementation(project(":providers:demo"))
    implementation(project(":providers:metadata-tmdb"))
    implementation(project(":providers:live-tv-m3u"))

    // AndroidX & TV Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Coroutines & Serialization & Coil
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
}
