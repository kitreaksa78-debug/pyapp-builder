plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "org.pyapp.aiphone"
    compileSdk = 34
    defaultConfig { applicationId = "org.pyapp.aiphone"; minSdk = 21; targetSdk = 34; versionCode = 13; versionName = "1.9.1" }
    buildTypes {
        debug { isMinifyEnabled = false }
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core-ktx:1.13.1") }
