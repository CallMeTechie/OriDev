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
    namespace = "dev.ori.feature.settings"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
        // Phase 11 PR 4a: enable instrumented tests for SettingsScreenLayoutTest
        // (the bail-out gate for the Phase 11 foundation PR stack — verifies
        // there is no padding leak between OriTopBar's bottom edge and the
        // first content row of SettingsScreen).
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
    implementation(project(":core:core-security"))
    implementation(project(":domain"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    implementation(libs.navigation.compose)
    implementation(libs.billing.ktx)
    // Phase 11 P1.2: AppPreferences DataStore aggregates all 7 settings
    // sections (Appearance/Terminal/Transfers/Security/Notifications) into
    // a single backing file ori_settings.preferences_pb.
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    // Phase 11 PR 4a — instrumented test scaffolding for SettingsScreenLayoutTest
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.compose.ui.test.manifest)

    debugImplementation(libs.compose.ui.tooling)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
