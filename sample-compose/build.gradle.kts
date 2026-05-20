// SPDX-License-Identifier: Apache-2.0
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.markusmock.purchasekit.sample.compose"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.markusmock.purchasekit.sample.compose"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
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
    implementation(project(":purchasekit-compose"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
}
