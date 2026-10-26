import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.voxtrics.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.voxtrics.demo"
        minSdk = 24
        // -PdemoTargetSdk=34 tries the SDK in an app that Android 15+ does not draw edge to edge.
        targetSdk = (findProperty("demoTargetSdk") as String?)?.toInt() ?: 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":voxtrics"))
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
