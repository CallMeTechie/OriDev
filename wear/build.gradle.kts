import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Shared version source of truth — see /version.properties.
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
    namespace = "dev.ori.wear"
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
        applicationId = "com.ori.dev.wear"
        minSdk = 34
        targetSdk = 36
        versionCode = vCode
        versionName = "$vMajor.$vMinor.$vPatch"
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-fonts"))
    implementation(project(":core:core-security"))
    implementation(project(":domain"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.horologist.compose.layout)
    implementation(libs.play.services.wearable)
    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.material)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ============================================================================
// Phase 11 / P0.10.4 — checkWearLeakage
// Mirror of :core:core-fonts:checkCoreFontsLeakage. Walks debugRuntimeClasspath
// via incoming.resolutionResult and asserts that no androidx.compose.material3
// (the PHONE Material 3 artifact) appears in the resolved component graph.
// Wear legitimately uses androidx.wear.compose:compose-material3 — that's a
// different artifact group and is allowed; only the phone variant is forbidden.
//
// Catches the regression where someone adds :core:core-ui as a wear dep by
// mistake (which would pull phone Compose Material 3 into the wear APK,
// defeating the whole point of the :core:core-fonts isolation in PR 1).
// ============================================================================
tasks.register("checkWearLeakage") {
    group = "verification"
    description = "Fails if :wear transitively depends on androidx.compose.material3 (phone variant)"
    val rootProvider = configurations.named("debugRuntimeClasspath")
        .flatMap { it.incoming.resolutionResult.rootComponent }
    doLast {
        val all = mutableSetOf<String>()
        fun walk(component: org.gradle.api.artifacts.result.ResolvedComponentResult) {
            if (!all.add(component.id.displayName)) return
            component.dependencies
                .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                .forEach { walk(it.selected) }
        }
        walk(rootProvider.get())
        // Match exactly androidx.compose.material3:material3 (phone variant).
        // androidx.wear.compose:compose-material3 is a different group and allowed.
        val leak = all.filter {
            val displayName = it
            displayName.startsWith("androidx.compose.material3:")
        }
        check(leak.isEmpty()) {
            "wear transitively depends on phone-side material3 (Compose isolation broken):\n" +
                leak.joinToString("\n")
        }
    }
}
tasks.named("check") { dependsOn("checkWearLeakage") }
