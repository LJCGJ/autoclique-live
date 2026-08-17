import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// -----------------------------------------------------------------------
// Assinatura do APK de release.
//
// A chave e as senhas NAO ficam no repositorio: moram em autoclique.jks e
// keystore.properties, os dois no .gitignore. Quem clonar o projeto sem
// esses arquivos ainda consegue compilar — o release sai sem assinatura de
// release (o Gradle usa a de debug), em vez de quebrar o build.
//
// Para gerar a sua:
//   keytool -genkeypair -v -keystore autoclique.jks -alias autoclique \
//           -keyalg RSA -keysize 2048 -validity 10950
// e crie keystore.properties com storePassword, keyPassword e keyAlias.
// -----------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreFile = rootProject.file("autoclique.jks")

val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

val podeAssinar = keystorePropsFile.exists() &&
    keystoreFile.exists() &&
    keystoreProps.getProperty("storePassword") != null

android {
    namespace = "com.autoclique.live"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autoclique.live"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (podeAssinar) {
            create("local") {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias") ?: "autoclique"
                keyPassword = keystoreProps.getProperty("keyPassword")
                    ?: keystoreProps.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            if (podeAssinar) {
                signingConfig = signingConfigs.getByName("local")
            } else {
                logger.warn(
                    "AVISO: keystore.properties ou autoclique.jks nao encontrados. " +
                        "O APK de release NAO sera assinado com a chave de release."
                )
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
