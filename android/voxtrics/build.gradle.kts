import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Reported to the backend with every response (appContext.sdkVersion) and readable as Voxtrics.SDK_VERSION.
val sdkVersion = "0.1.0"

android {
    namespace = "io.voxtrics.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // 11, not 17: an app compiling against the library may still target an older JVM.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    // A library's public surface is its contract with every app using it: nothing is public by accident.
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // FragmentActivity is in the public API (Voxtrics.showSurvey).
    api(libs.androidx.fragment.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.webkit)

    testImplementation(libs.junit)
    // android.jar's org.json is a stub in JVM tests; the real one parses the bridge messages there.
    testImplementation(libs.json)
}
