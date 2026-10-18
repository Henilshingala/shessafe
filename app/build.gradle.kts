plugins {
    id("com.android.application") version "8.5.0"
    kotlin("android") version "1.9.10"
    id("com.google.devtools.ksp") version "1.9.10-1.0.13"
    // NOTE: google-services plugin requires google-services.json in app/ directory.
    // The app compiles without it (Firebase OTP stubbed), but Firebase Phone Auth will
    // not work at runtime until google-services.json is added.
    // id("com.google.gms.google-services")  // Uncomment after adding google-services.json
}

android {
    namespace = "com.womensafety.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.womensafety.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        viewBinding = false   // No XML layouts; was incorrectly true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    // SEC-01 FIX: Signing config reads from environment variables / local.properties
    // Never hardcode keystore passwords in source code.
    // For CI: set env vars KEYSTORE_PATH, KEYSTORE_PASS, KEY_ALIAS, KEY_PASS
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystorePass = System.getenv("KEYSTORE_PASS")
            val keyAlias = System.getenv("KEY_ALIAS")
            val keyPass = System.getenv("KEY_PASS")
            if (keystorePath != null && keystorePass != null && keyAlias != null && keyPass != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePass
                this.keyAlias = keyAlias
                keyPassword = keyPass
            }
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only apply release signing if env vars are set
            val hasSigningConfig = System.getenv("KEYSTORE_PATH") != null
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.11.0")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.foundation:foundation:1.5.4")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Location Services for Phase 4 FusedLocationProviderClient
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Security - Encrypted SharedPreferences
    // SEC-02 FIX: Upgraded to stable release from alpha
    implementation("androidx.security:security-crypto:1.0.0")

    // Phone number library for proper country-code-aware normalization
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.27")

    // Firebase BOM (commented out until google-services.json is added)
    // implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    // implementation("com.google.firebase:firebase-auth-ktx")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
