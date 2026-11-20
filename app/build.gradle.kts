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
        versionCode = 14
        versionName = "1.4"
    }

    // Chave da versão oficial: fica guardada em segredo no GitHub (Settings > Secrets).
    // Sem ela, o GitHub não gera a versão oficial.
    val chaveArquivo = System.getenv("KEYSTORE_FILE")
    val chaveSenha = System.getenv("KEYSTORE_PASS")

    signingConfigs {
        if (chaveArquivo != null && chaveSenha != null) {
            create("oficial") {
                storeFile = file(chaveArquivo)
                storePassword = chaveSenha
                keyAlias = "valeapena"
                keyPassword = chaveSenha
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (chaveArquivo != null && chaveSenha != null) {
                signingConfig = signingConfigs.getByName("oficial")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
