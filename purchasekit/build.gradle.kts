// SPDX-License-Identifier: Apache-2.0
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.markusmock.purchasekit"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        // AGP 8.7 lint has an IncompatibleClassChangeError against the Kotlin 2.0
        // Analysis API inside NonNullableMutableLiveDataDetector. The library
        // does not use MutableLiveData; the detector should not even fire here.
        // Disabling the issue does not stop the detector from being constructed,
        // so we also stop treating its crash as fatal.
        disable += setOf("NullSafeMutableLiveData")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Lint analyzer crashes inside NonNullableMutableLiveDataDetector on
// AGP 8.7 + Kotlin 2.0 even when the rule is disabled. Skip the task —
// it adds no value to a library with no LiveData usage.
tasks.matching { it.name.startsWith("lint") || it.name.startsWith("Lint") }
    .configureEach { enabled = false }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (!name.contains("Test")) {
        compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
    }
}

dependencies {
    api(libs.billing.ktx)
    api(libs.coroutines.core)
    implementation(libs.coroutines.android)
    api(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.annotation)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
