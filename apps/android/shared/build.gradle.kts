plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    sourceSets {
        val commonMain by getting
    }
}

android {
    namespace = "com.corestate.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
}