pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OriDev"

include(":app")
include(":core:core-common")
include(":core:core-fonts")
include(":core:core-ui")
include(":core:core-network")
include(":core:core-security")
include(":core:core-ai")
include(":core-billing")
include(":core-ads")
include(":domain")
include(":data")
include(":feature-filemanager")
include(":feature-terminal")
include(":feature-connections")
include(":feature-transfers")
include(":feature-proxmox")
include(":feature-editor")
include(":feature-settings")
include(":feature-onboarding")
include(":feature-premium")
include(":wear")
include(":baselineprofile")
