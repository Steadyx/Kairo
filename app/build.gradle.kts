import io.gitlab.arturbosch.detekt.Detekt
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ksp)
}

private data class ReleaseSigningProperties(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String
)

private val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
private val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use { input ->
            load(input)
        }
    }
}

private fun releaseSigningProperty(
    propertyName: String,
    environmentName: String
): String? {
    return (
        releaseSigningProperties.getProperty(propertyName)
            ?: providers.environmentVariable(environmentName).orNull
    )?.trim()?.takeIf { it.isNotEmpty() }
}

private fun releaseSigningStoreFile(path: String): File {
    return File(path).takeIf { it.isAbsolute } ?: rootProject.file(path)
}

private val releaseSigning = releaseSigningProperty(
    propertyName = "storeFile",
    environmentName = "KAIRO_RELEASE_STORE_FILE"
)?.let { storeFile ->
    val storePassword = releaseSigningProperty(
        propertyName = "storePassword",
        environmentName = "KAIRO_RELEASE_STORE_PASSWORD"
    )
    val keyAlias = releaseSigningProperty(
        propertyName = "keyAlias",
        environmentName = "KAIRO_RELEASE_KEY_ALIAS"
    )
    val keyPassword = releaseSigningProperty(
        propertyName = "keyPassword",
        environmentName = "KAIRO_RELEASE_KEY_PASSWORD"
    )

    if (storePassword == null || keyAlias == null || keyPassword == null) {
        null
    } else {
        ReleaseSigningProperties(
            storeFile = releaseSigningStoreFile(storeFile),
            storePassword = storePassword,
            keyAlias = keyAlias,
            keyPassword = keyPassword
        )
    }
}

private val releaseBuildRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(':').contains("Release")
}

if (releaseBuildRequested && releaseSigning == null) {
    throw GradleException(
        "Release signing is not configured. Run ./scripts/setup-release-signing.sh " +
            "or create keystore.properties from keystore.properties.example."
    )
}

if (releaseBuildRequested && releaseSigning?.storeFile?.isFile == false) {
    throw GradleException(
        "Release keystore does not exist at ${releaseSigning.storeFile}. " +
            "Update storeFile in keystore.properties or rerun ./scripts/setup-release-signing.sh."
    )
}

android {
    namespace = "com.kairo.reader"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kairo.reader"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 14
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            releaseSigning?.let { signing ->
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            // Kairo uses single-process DataStore, so its multi-process shared counter is unused.
            excludes += "**/libdatastore_shared_counter.so"
        }
    }
    lint {
        // AGP stays within the installed Android Studio support range; keep updates visible in lint.
        informational += "AndroidGradlePluginVersion"
        warningsAsErrors = true
        error += "AutoboxingStateCreation"
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/detekt.yml"))
    autoCorrect = false

    // Big speed win on multicore machines
    parallel = true
}

tasks.withType<Detekt>().configureEach {
    exclude("**/build/**", "**/generated/**")
}


ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/*.gradle.kts")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.jsoup)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    implementation(libs.pdfbox.android) {
        // Kairo rejects encrypted PDFs; public-key cryptography providers are unnecessary here.
        exclude(group = "org.bouncycastle")
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
