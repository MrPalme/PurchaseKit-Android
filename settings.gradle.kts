// SPDX-License-Identifier: Apache-2.0
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PurchaseKit-Android"
include(":purchasekit")
include(":purchasekit-compose")
include(":sample-compose")
include(":sample-views")
