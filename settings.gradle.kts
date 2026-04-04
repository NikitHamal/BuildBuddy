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

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BuildBuddy"

include(":app")
include(":core:designsystem")
include(":core:model")
include(":core:data")
include(":core:network")
include(":core:ui")
include(":feature:home")
include(":feature:onboarding")
include(":feature:project")
include(":feature:editor")
include(":feature:agent")
include(":feature:build")
include(":feature:settings")
