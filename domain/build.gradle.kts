// :domain — 순수 Kotlin(JVM) 모듈. Android/Compose/Hilt 비의존(iOS Domain 대응).
// 엔티티·Repository 인터페이스·UseCase만. 에러 타입(MutterError)도 여기(순수 Kotlin) 소유.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}
