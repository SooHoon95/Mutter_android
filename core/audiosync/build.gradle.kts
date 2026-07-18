// :core:audiosync — 편지 음악 재생(iOS AudioSync 이식).
// SoundCloud = 공식 Widget(숨김 WebView + JS 브리지, 라이선스 준수), Hosted = Media3(ExoPlayer).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.efreedom.mutter.core.audiosync"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":domain"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)

    implementation(libs.kotlinx.coroutines.core)
}
