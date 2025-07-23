// CoreState-v2/settings.gradle.kts
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

rootProject.name = "CoreState-v2"

// Include application modules
include(":apps:android:androidApp")
include(":apps:android:shared")

// Include service modules
include(":services:backup-engine")
include(":services:analytics-engine")
// Add other service modules here as they are implemented