import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.play.publisher)
}

// Single source of truth for app version — rewritten by
// .github/workflows/auto-tag.yml based on conventional commits.
val versionProps = Properties().apply {
    rootProject.file("version.properties").reader().use { load(it) }
}
val vMajor = versionProps.getProperty("VERSION_MAJOR").toInt()
val vMinor = versionProps.getProperty("VERSION_MINOR").toInt()
val vPatch = versionProps.getProperty("VERSION_PATCH").toInt()
val vCode = versionProps.getProperty("VERSION_CODE").toInt()

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

android {
    namespace = "dev.ori.app"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "release.keystore"
            storeFile = file(keystorePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
            enableV1Signing = false // minSdk 34, avoid Janus CVE-2017-13156
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.ori.dev"
        minSdk = 34
        targetSdk = 36
        versionCode = vCode
        versionName = "$vMajor.$vMinor.$vPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "ACRA_BACKEND_URL",
            "\"${project.findProperty("acra.backend.url") ?: "https://acra.invalid"}\"",
        )
        buildConfigField(
            "String",
            "ACRA_BASIC_AUTH_LOGIN",
            "\"${project.findProperty("acra.basic.auth.login") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "ACRA_BASIC_AUTH_PASSWORD",
            "\"${project.findProperty("acra.basic.auth.password") ?: ""}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (!System.getenv("KEYSTORE_PASSWORD").isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug") // local dev fallback
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Google Play Store publishing — activated only when PLAY_SERVICE_ACCOUNT_JSON_PATH
// points to a valid credentials file. Without it, the plugin is still configured
// (and plan/publish tasks are registered) but won't run successfully, which is
// the intended safe default for local/PR builds.
play {
    serviceAccountCredentials.set(
        file(System.getenv("PLAY_SERVICE_ACCOUNT_JSON_PATH") ?: "/dev/null")
    )
    track.set("internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT)
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-security"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature-connections"))
    implementation(project(":feature-filemanager"))
    implementation(project(":feature-terminal"))
    implementation(project(":feature-transfers"))
    implementation(project(":feature-proxmox"))
    implementation(project(":feature-editor"))
    implementation(project(":feature-settings"))
    implementation(project(":feature-onboarding"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.splashscreen)
    implementation(libs.window)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.acra.http)
    implementation(libs.acra.limiter)

    // Performance: ProfileInstaller pulls in the baseline profile at install time.
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    // LeakCanary in debug builds only — never shipped to release.
    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.vintage.engine)
    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.datastore.preferences)

    // Phase 12 Tier 3 T3c — Robolectric for TransferNotificationManager tests.
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.test.ext.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

