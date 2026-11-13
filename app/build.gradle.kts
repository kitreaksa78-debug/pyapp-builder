plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.groqpool"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.groqpool"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
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
