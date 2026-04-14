plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

android {
    namespace = "dev.ori.core.fonts"
    compileSdk = 36

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// ----------------------------------------------------------------------------
// Phase 11 / P0.1 — minimal Compose dependency set
//
// core-fonts is shared by both phone (:core:core-ui) and Wear (:wear). The
// architect review of plan v1 found that adding :core:core-ui as a wear dep
// would pull phone-Compose Material 3 into the wear APK because :core:core-ui
// transitively depends on androidx.compose.material3. Plan v6 isolates fonts
// in this module which depends on **only** ui-text + ui-unit, with material3
// surgically excluded as a defensive measure.
//
// The :core:core-fonts:checkCoreFontsLeakage task below verifies the absence
// of any androidx.compose.material3 entry in the resolved runtime classpath
// and is wired into `check`.
// ----------------------------------------------------------------------------
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui.text) {
        exclude(group = "androidx.compose.material3")
    }
    implementation(libs.compose.ui.unit) {
        exclude(group = "androidx.compose.material3")
    }
}

// ============================================================================
// P0.10.3 — checkCoreFontsLeakage
// Fails the build if core-fonts transitively depends on androidx.compose.material3
// (which would defeat the whole purpose of having a separate fonts module —
// the wear APK would inherit phone Compose). Uses the modern
// incoming.resolutionResult API (not the deprecated resolvedConfiguration) and
// walks the resolved component graph.
// ============================================================================
tasks.register("checkCoreFontsLeakage") {
    group = "verification"
    description = "Fails if core-fonts transitively depends on androidx.compose.material3"
    // rootComponent is itself a Provider, so we flatMap to unwrap Provider<Provider<X>> → Provider<X>
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
        val leak = all.filter { it.contains("androidx.compose.material3") }
        check(leak.isEmpty()) {
            "core-fonts transitively depends on material3 (phone Compose leak):\n" +
                leak.joinToString("\n")
        }
    }
}
tasks.named("check") { dependsOn("checkCoreFontsLeakage") }

// ============================================================================
// P0.10.5 — checkFontBudget
// Fails if the combined size of TTF files in src/main/res/font exceeds the
// configured byte budget. Default: 1_500_000 bytes (1.5 MB) per plan v6 §P0.1.
// Override at command line: -Pfont.budget=N (bytes). Used by the weekly
// ci-self-test workflow with -Pfont.budget=0 as a positive control to verify
// the task still rejects bad input.
// ============================================================================
tasks.register("checkFontBudget") {
    group = "verification"
    description = "Fails if font assets exceed the configured byte budget"
    val fontDir = file("src/main/res/font")
    val defaultBudget = 1_500_000L
    val budget = (project.findProperty("font.budget") as String?)?.toLongOrNull() ?: defaultBudget
    doLast {
        require(fontDir.exists()) { "Font directory missing: $fontDir" }
        val files = fontDir.walkTopDown()
            .filter { it.isFile && it.extension == "ttf" }
            .toList()
        val total = files.sumOf { it.length() }
        val report = files.joinToString("\n") { "  ${it.name}: ${it.length() / 1024} KB" }
        logger.lifecycle("Font budget: $total / $budget bytes\n$report")
        check(total <= budget) {
            "Font assets exceed budget: $total > $budget bytes. " +
                "Run pyftsubset or remove unused weights.\n$report"
        }
    }
}
tasks.named("check") { dependsOn("checkFontBudget") }
