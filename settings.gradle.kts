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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RoutePrice"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":feature:home")
include(":feature:trip")
include(":feature:quote")
include(":feature:history")
include(":feature:settings")
