import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Optional release signing: create `keystore.properties` in the project root (see
// keystore.properties.example). Without it the release APK is signed with the local
// Android *debug* key, which is still installable but NOT suitable for Google Play.
val keystorePropsFile = rootProject.file("keystore.properties")
val hasReleaseKeystore = keystorePropsFile.exists()
val keystoreProps = Properties()
if (hasReleaseKeystore) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {
    namespace = "com.chikchik.posecamera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chikchik.posecamera"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks code + resources -> smaller, faster APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Universal APK: no ABI / density splits, no `ndk.abiFilters`. The app has no native
    // (.so) libraries at all, so the single APK runs on arm64-v8a, armeabi-v7a, x86 and x86_64.
    splits {
        abi {
            isEnable = false
        }
        density {
            isEnable = false
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // LiveData (CameraX exposes zoom / camera state as LiveData)
    implementation(libs.androidx.lifecycle.livedata.core)

    // Runtime permissions helper for Compose
    implementation(libs.accompanist.permissions)

    debugImplementation(libs.androidx.ui.tooling)
}
