import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.google.services) // Use the alias from your TOML
}

// --- Lógica para leer local.properties ---
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.diariosync"
    compileSdk = 35 // Changed to 35 (Stable) unless you specifically need 36 preview

    // --- Configuración de Firma ---
    signingConfigs {
        create("release") {
            // Buscamos los valores en el local.properties
            storeFile = localProperties.getProperty("release.keystore.path")?.let { file(it) }
            storePassword = localProperties.getProperty("release.keystore.password")
            keyAlias = localProperties.getProperty("release.key.alias")
            keyPassword = localProperties.getProperty("release.key.password")
        }
    }

    defaultConfig {
        applicationId = "com.example.diariosync"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "EMAILJS_PRIVATE_KEY",
            "\"${localProperties.getProperty("emailjs.private.key", "")}\""
        )
        buildConfigField(
            "String",
            "EMAILJS_USER_ID",
            "\"${localProperties.getProperty("emailjs.user.id", "")}\""
        )
        buildConfigField(
            "String",
            "EMAILJS_SERVICE_ID",
            "\"${localProperties.getProperty("emailjs.service.id", "")}\""
        )
        buildConfigField(
            "String",
            "EMAILJS_TEMPLATE_ID_CIERRECAJA",
            "\"${localProperties.getProperty("emailjs.template.id.cierrecaja", "")}\""
        )
        buildConfigField(
            "String",
            "EMAILJS_TEMPLATE_ID_PASSWORDCAMBIO",
            "\"${localProperties.getProperty("emailjs.template.id.passwordcambio", "")}\""
        )
        buildConfigField(
            "String",
            "DROPBOX_REFRESH_TOKEN",
            "\"${localProperties.getProperty("dropbox.refresh.token", "")}\""
        )
        buildConfigField(
            "String",
            "DROPBOX_APP_KEY",
            "\"${localProperties.getProperty("dropbox.app.key", "")}\""
        )
        buildConfigField(
            "String",
            "DROPBOX_APP_SECRET",
            "\"${localProperties.getProperty("dropbox.app.secret", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Vinculamos la firma de release aquí
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // UI KTX (Fixes 'viewModels' unresolved reference)
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.activity:activity-ktx:1.9.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Firebase (Fixed)
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-common")

    // WorkManager & Coroutines
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Excel & Lifecycle
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    // Splashscreen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Swipe refresh
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    //For Github API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    //GMS
    implementation("com.google.android.gms:play-services-base:18.4.0")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}