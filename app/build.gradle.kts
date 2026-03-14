plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.rgbpos.bigs"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rgbpos.bigs"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.3.0"

        buildConfigField("String", "API_BASE_URL", "\"https://bigs.rgbpos.com/api/\"")
        buildConfigField("String", "UPDATE_URL", "\"https://bigs.rgbpos.com/downloads/version.json\"")
    }

    signingConfigs {
        create("release") {
            val ks = file("keystore/bigs-pos.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "bigspos2026"
                keyAlias = "bigspos"
                keyPassword = "bigspos2026"
            }
        }
    }

    buildTypes {
        debug {
            val ks = file("keystore/bigs-pos.jks")
            if (ks.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val ks = file("keystore/bigs-pos.jks")
            if (ks.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // DataStore for token persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room (offline database)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
