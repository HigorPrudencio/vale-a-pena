plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hjvision.valeapena"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hjvision.valeapena"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
    }

    signingConfigs {
        create("fixo") {
            storeFile = file("valeapena.keystore")
            storePassword = "valeapena"
            keyAlias = "valeapena"
            keyPassword = "valeapena"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixo") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixo")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
