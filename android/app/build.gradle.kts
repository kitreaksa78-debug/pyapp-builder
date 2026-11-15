plugins {
    id("com.android.application")
}

android {
    namespace = "com.citydrive"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.citydrive"
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
    implementation("androidx.appcompat:appcompat:1.6.1")
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
