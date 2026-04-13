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

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${libs.versions.detekt.get()}")
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
