import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

// Reported to the backend with every response (appContext.sdkVersion) and readable as Voxtrics.SDK_VERSION.
val sdkVersion = "0.1.1"

android {
    namespace = "io.voxtrics.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersion\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        // The instrumented tests' own app: below this, Android shows its "built for an older version" dialog over it,
        // and that dialog takes the Back presses and taps the tests make.
        targetSdk = 36
    }
}

kotlin {
    // A library's public surface is its contract with every app using it: nothing is public by accident.
    explicitApi()
    // Built with a current Kotlin, readable by an app on Kotlin 2.0: a compiler reads a library's metadata only up to
    // one version ahead of its own (2.1 here), and the kotlin-stdlib dependency would pull an app's stdlib up to the
    // build's. The compiler warns that 2.1 is deprecated; that is the price of reaching those apps.
    coreLibrariesVersion = "2.1.21"
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
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

    // On a device or emulator, against a fake survey page served from the test itself - no backend.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.mockwebserver)
}

// Published to Maven Central as io.voxtrics:voxtrics-android - the coordinates the Voxtrics admin's install code shows.
// `./gradlew :voxtrics:publishToMavenCentral` uploads a deployment to the Central Portal, released there by hand. It
// needs, in ~/.gradle/gradle.properties, mavenCentralUsername / mavenCentralPassword (a Portal user token) and a
// signing key: `signing.gnupg.keyName` for a key in the local GnuPG (gpg asks for its passphrase), or
// `signingInMemoryKey` (+ `signingInMemoryKeyPassword`) on a CI. `./gradlew :voxtrics:publishToMavenLocal` needs none.
val signsWithGpg = providers.gradleProperty("signing.gnupg.keyName").isPresent

mavenPublishing {
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true))
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    // Maven Central takes signed artifacts only, so a missing key fails the upload there, not here.
    if (signsWithGpg || providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

    coordinates("io.voxtrics", "voxtrics-android", sdkVersion)
    pom {
        name.set("Voxtrics Android SDK")
        description.set("Shows Voxtrics in-app surveys in an Android app: a bottom sheet over the app's screen, opened when the app asks.")
        inceptionYear.set("2026")
        url.set("https://github.com/voxtrics/mobile-sdk")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("voxtrics")
                name.set("Voxtrics")
                url.set("https://voxtrics.io")
            }
        }
        scm {
            url.set("https://github.com/voxtrics/mobile-sdk")
            connection.set("scm:git:https://github.com/voxtrics/mobile-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/voxtrics/mobile-sdk.git")
        }
    }
}

// The signing plugin comes with the publish plugin, so there is no `signing {}` accessor here.
if (signsWithGpg) extensions.configure<SigningExtension> { useGpgCmd() }
