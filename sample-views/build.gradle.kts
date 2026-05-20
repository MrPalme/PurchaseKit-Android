// SPDX-License-Identifier: Apache-2.0
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.markusmock.purchasekit.sample.views"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.markusmock.purchasekit.sample.views"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.matching { it.name.startsWith("lint") || it.name.startsWith("Lint") }
    .configureEach { enabled = false }

dependencies {
    implementation(project(":purchasekit"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
}
