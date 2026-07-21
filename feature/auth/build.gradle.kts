// :feature:auth — 로그인/온보딩 화면(iOS AuthFeature 대응). Compose + Hilt ViewModel.
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val socialProps = Properties().apply {
    listOf("local.properties", "secrets.properties").forEach { name ->
        rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
}

android {
    namespace = "com.efreedom.mutter.feature.auth"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        // Google id_token 발급용 Web 클라이언트 ID (Credential Manager serverClientId).
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${socialProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"",
        )
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
    implementation(project(":uicomponent"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // 소셜 로그인 — Google(Credential Manager) + Kakao SDK
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    implementation(libs.kakao.user)
    implementation(libs.kotlinx.coroutines.core)
}
