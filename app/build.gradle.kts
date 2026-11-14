plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tn.ennour.maan"
    compileSdk = 34

    defaultConfig {
        applicationId = "tn.ennour.maan"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "1.7"
    }
    // مفتاح توقيع ثابت (يأتي من أسرار GitHub) حتى تتثبّت التحديثات فوق النسخة القديمة.
    // بدون الأسرار يُستعمل مفتاح مؤقت كما كان.
    val ksPath = System.getenv("MAAN_KEYSTORE_FILE")
    val hasFixedKey = ksPath != null && file(ksPath).exists()
    signingConfigs {
        if (hasFixedKey) create("fixed") {
            storeFile = file(ksPath!!)
            storePassword = System.getenv("MAAN_KEYSTORE_PASSWORD")
            keyAlias = "maan"
            keyPassword = System.getenv("MAAN_KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        debug { if (hasFixedKey) signingConfig = signingConfigs.getByName("fixed") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
