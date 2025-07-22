plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.corestate.androidApp"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.corestate.androidApp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":apps:android:shared"))
}