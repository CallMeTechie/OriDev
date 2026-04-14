plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

android {
    namespace = "dev.ori.feature.editor"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
        // Phase 11 PR 4b: enable instrumented tests for SoraThemingSpike
        // (P0.8 — proves Sora-Editor accepts JetBrainsMono typeface and a
        // GitHub-palette EditorColorScheme, which P2.2 needs).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":core:core-ui"))
    implementation(project(":domain"))

    implementation(libs.sora.editor)
    implementation(libs.sora.editor.textmate)
    implementation(libs.java.diff.utils)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    implementation(libs.navigation.compose)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    // Phase 11 PR 4b — instrumented test scaffolding for SoraThemingSpike
    androidTestImplementation(project(":core:core-fonts"))
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.truth)

    debugImplementation(libs.compose.ui.tooling)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
