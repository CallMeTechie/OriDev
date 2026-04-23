plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:core-common"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("javax.inject:javax.inject:1")
    // Pure-JVM (no-Android) DataStore. Keeps `domain` a plain `kotlin.jvm`
    // module so the CLAUDE.md "domain has no Android deps" rule holds.
    // The Android-specific Hilt binding that materialises a DataStore from
    // an ApplicationContext lives downstream (see Task 14.6).
    implementation(libs.datastore.preferences.core)
    // kotlinx.serialization is pure Kotlin (no Android deps) — used by the
    // resume-state types (TabMemo) that round-trip through DataStore.
    implementation(libs.kotlinx.serialization.json)

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
