import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 소셜 키를 secrets.properties(+local.properties)에서 읽어 BuildConfig/매니페스트로 주입. 둘 다 gitignore.
val socialProps = Properties().apply {
    listOf("local.properties", "secrets.properties").forEach { name ->
        rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
}

android {
    namespace = "com.efreedom.mutter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.efreedom.mutter"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        val kakaoKey = socialProps.getProperty("KAKAO_NATIVE_APP_KEY", "")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
        // Kakao 리다이렉트 스킴(kakao{nativeKey}://oauth) — AuthCodeHandlerActivity용.
        manifestPlaceholders["kakaoNativeAppKey"] = kakaoKey
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    // core
    implementation(project(":uicomponent"))
    implementation(project(":core:navigation"))
    implementation(project(":core:network")) // OAuth 리다이렉트(handleDeeplinks) 처리용 SupabaseProvider 주입

    // features
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:threads"))
    implementation(project(":feature:inbox"))
    implementation(project(":feature:connections"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:compose"))
    implementation(project(":feature:viewer"))
    implementation(project(":feature:delivery"))
    implementation(project(":feature:legal"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // navigation (type-safe)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Kakao SDK 초기화(Application)
    implementation(libs.kakao.user)

    // 런치스크린(Android 12 스플래시 백포트 — 26~35 동일하게 아이보리+마크)
    implementation(libs.androidx.core.splashscreen)
}
