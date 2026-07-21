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
        // Kakao SDK
        maven { setUrl("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
}

rootProject.name = "Mutter"
include(":app")
include(":domain")
include(":core:network")
include(":core:common")
include(":uicomponent")
include(":core:navigation")
include(":core:audiosync")
include(":data")
include(":feature:auth")
include(":feature:compose")
include(":feature:viewer")
include(":feature:delivery")
include(":feature:inbox")
include(":feature:connections")
include(":feature:threads")
include(":feature:profile")
include(":feature:home")
include(":feature:legal")
include(":feature:maintab")
