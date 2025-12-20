// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        apply(plugin = "dev.detekt")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        apply(plugin = "dev.detekt")
    }
}

tasks.matching { it.name == "detekt" }.configureEach {
    dependsOn(":app:detektMain")
    // optionally:
    // dependsOn(":app:detektTest")
}
