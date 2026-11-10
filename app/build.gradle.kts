plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "org.pyapp.qapyauto"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.pyapp.qapyauto"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }

    }

    signingConfigs {
        create("stable") {
            storeFile = file("../signing/debug.p12")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

chaquopy {
    defaultConfig {
        version = "3.8"
    }
}
