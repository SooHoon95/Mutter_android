import java.util.Properties

// :data — Repository 구현 + DTO 매핑 + 에러 매퍼(iOS Infrastructure 대응). Hilt 사용(허용 모듈).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 보안 값을 secrets.properties(+local.properties)에서 읽어 BuildConfig로 주입. 둘 다 gitignore.
val localProps = Properties().apply {
    listOf("local.properties", "secrets.properties").forEach { name ->
        rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
}

android {
    namespace = "com.efreedom.mutter.data"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("SUPABASE_ANON_KEY", "")}\"")
    }
    buildTypes {
        // 개발 서버 — secrets.properties(또는 local.properties)에 DEV_SUPABASE_URL이 있으면 debug 빌드만 그 서버를 본다.
        // 없으면 debug도 운영 서버(SUPABASE_URL)를 그대로 쓴다. release는 영향이 없다.
        debug {
            localProps.getProperty("DEV_SUPABASE_URL")?.takeIf { it.isNotBlank() }?.let { devUrl ->
                buildConfigField("String", "SUPABASE_URL", "\"$devUrl\"")
                buildConfigField(
                    "String",
                    "SUPABASE_ANON_KEY",
                    "\"${localProps.getProperty("DEV_SUPABASE_ANON_KEY", "")}\"",
                )
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:network"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.billing.ktx)

    testImplementation(libs.junit)
}
