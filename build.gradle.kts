plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/detekt.yml")
    parallel = true
    source.setFrom(files(rootProject.subprojects.map { "${it.projectDir}/src" }))
}

// Exclude vendored third-party source from detekt + lint analysis. Phase 11
// PR 3 vendors the icons-lucide-cmp module from composablehorizons/compose-icons
// (1666 source files; we keep 68 of them) at a pinned tag with package and
// receiver names rewritten via sed. The vendored files do not follow our
// detekt formatting rules and we deliberately do not modify them so that
// future re-vendor diffs against upstream stay clean.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/dev/ori/core/ui/icons/lucide/**")
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    exclude("**/dev/ori/core/ui/icons/lucide/**")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${libs.versions.detekt.get()}")
}

// KT-73255: opt into the future-safe Kotlin annotation-default-target so that
// annotations declared on constructor `val` parameters (e.g. Hilt's
// `@ApplicationContext`, `@Inject`, custom qualifiers) are applied to both
// the parameter AND the generated property/field instead of only the
// parameter. Without this flag the Kotlin compiler floods every build with
// "This annotation is currently applied to the value parameter only, but in
// the future it will also be applied to field" warnings. Applying the flag
// here (rather than in each module's `kotlin { compilerOptions { ... } }`
// block) keeps the setting centralised so future Kotlin upgrades that change
// the default don't surprise us.
// See https://youtrack.jetbrains.com/issue/KT-73255
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }
}

// JaCoCo coverage for all subprojects with Kotlin
subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("org.jetbrains.kotlin.android") || plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
            apply(plugin = "jacoco")

            tasks.withType<JacocoReport> {
                dependsOn(tasks.withType<Test>())
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                    csv.required.set(false)
                }
            }
        }
    }
}

// Aggregate task to run all JaCoCo reports
tasks.register("jacocoTestReport") {
    group = "verification"
    description = "Generate JaCoCo coverage reports for all modules"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("jacocoTestReport") })
}
