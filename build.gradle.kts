// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.ksp.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "io.gitlab.arturbosch.detekt")
    }
}

tasks.register("detektFull") {
    group = "verification"
    description = "Runs Detekt across the app module"
    dependsOn(":app:detekt")
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs formatting, Detekt, debug compilation, and unit tests"
    dependsOn(
        "detektFull",
        ":app:ktlintCheck",
        ":app:compileDebugKotlin",
        ":app:testDebugUnitTest",
    )
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs the complete deterministic CI quality gate, including Android Lint and debug APK assembly"
    dependsOn("qualityCheck", ":app:lintDebug", ":app:assembleDebug")
}
