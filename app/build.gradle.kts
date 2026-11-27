plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.github.jk1.dependency-license-report") version "3.1.5"
}

val releaseStoreFile = providers.environmentVariable("ONYX_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ONYX_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ONYX_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ONYX_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

// Select one dependency graph per report so shipped runtime and test-only
// dependencies can be reviewed separately. Override with -PlicenseAuditConfiguration=...
val licenseAuditConfiguration = providers.gradleProperty("licenseAuditConfiguration")
    .orElse("releaseRuntimeClasspath")
    .get()

android {
    namespace = "com.onyx.perfmonitor"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.onyx.perfmonitor"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        aidl = true
    }
}

// Keep resolved direct and transitive versions stable once lockfiles are generated.
configurations.configureEach {
    resolutionStrategy.activateDependencyLocking()
}

// The license-report plugin does not support Gradle configuration cache yet.
// Run its task with --no-configuration-cache --no-parallel (see README).
licenseReport {
    configurations = arrayOf(licenseAuditConfiguration)
    outputDir = layout.buildDirectory
        .dir("reports/dependency-license/$licenseAuditConfiguration")
        .get()
        .asFile
        .absolutePath
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}