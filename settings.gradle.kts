// CoreState-v2/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        
        // JitPack for GitHub dependencies like MPAndroidChart
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
        
        // WebRTC repository
        maven {
            name = "WebRTC"
            url = uri("https://maven.google.com")
        }
        
        // Additional repositories for dependencies
        maven {
            name = "Sonatype Snapshots"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
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