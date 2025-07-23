plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("com.google.protobuf") version "0.9.4"
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    // iOS targets temporarily removed due to CI build issues
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.0")
                
                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                
                // Networking
                implementation("io.ktor:ktor-client-core:2.3.5")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
                implementation("io.ktor:ktor-client-logging:2.3.5")
                
                // UUID
                implementation("com.benasher44:uuid:0.8.2")
                
                // Logging
                implementation("co.touchlab:kermit:2.0.2")
                
                // Settings/Preferences
                implementation("com.russhwolf:multiplatform-settings:1.1.1")
                
                // SQL Database
                implementation("app.cash.sqldelight:runtime:2.0.0")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.0")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
        
        val androidMain by getting {
            dependencies {
                // Android-specific networking
                implementation("io.ktor:ktor-client-okhttp:2.3.5")
                
                // Android SQLite
                implementation("app.cash.sqldelight:android-driver:2.0.0")
                
                // Android-specific crypto
                implementation("androidx.security:security-crypto:1.1.0-alpha06")
                
                // gRPC for Android
                implementation("io.grpc:grpc-okhttp:1.58.0")
                implementation("io.grpc:grpc-protobuf-lite:1.58.0")
                implementation("io.grpc:grpc-stub:1.58.0")
                
                // WebRTC
                implementation("org.webrtc:google-webrtc:1.0.32006")
            }
        }
        
        // iOS dependencies temporarily removed
        // val iosMain by getting {
        //     dependencies {
        //         // iOS-specific networking
        //         implementation("io.ktor:ktor-client-darwin:2.3.5")
        //         
        //         // iOS SQLite
        //         implementation("app.cash.sqldelight:native-driver:2.0.0")
        //     }
        // }
    }
}

android {
    namespace = "com.corestate.shared"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 26
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        buildConfig = true
    }
}

// Protocol Buffers configuration
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.24.4"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.58.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.0:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}