plugins {
    id("com.android.application")
}

android {
    namespace = "com.system.update.service"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.system.update.service"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}

// PyApp Builder: Kotlin 1.8+ merged jdk7/jdk8 into kotlin-stdlib.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" &&
                (requested.name == "kotlin-stdlib-jdk7" ||
                 requested.name == "kotlin-stdlib-jdk8")) {
            useVersion("1.8.22")
        }
    }
}
