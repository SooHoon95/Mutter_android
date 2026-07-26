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
    // Play 요구: 2026-08-31부터 신규 앱·업데이트는 API 36(Android 16) 타겟 필수.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.efreedom.mutter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        val kakaoKey = socialProps.getProperty("KAKAO_NATIVE_APP_KEY", "")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
        // Kakao 리다이렉트 스킴(kakao{nativeKey}://oauth) — AuthCodeHandlerActivity용.
        manifestPlaceholders["kakaoNativeAppKey"] = kakaoKey
    }

    // 업로드 키 서명. 키스토어는 저장소 밖에 두고 경로·비밀번호는 secrets.properties에서 읽는다(gitignore).
    // 값이 없으면 signingConfig를 붙이지 않아 로컬 빌드는 그대로 동작한다.
    val releaseStorePath = socialProps.getProperty("RELEASE_STORE_FILE", "")
    val releaseStore = releaseStorePath.takeIf { it.isNotBlank() }?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = socialProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = socialProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = socialProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
