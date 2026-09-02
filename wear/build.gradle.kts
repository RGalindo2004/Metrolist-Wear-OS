import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.metrolist.music.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.metrolist.music"
        minSdk = 26
        targetSdk = 35
        versionCode = 156
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.activity)
    implementation(libs.hilt.navigation)
    
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.horologist.audio.ui)
    implementation(libs.horologist.media.ui)
    implementation(libs.horologist.layout)
    implementation(libs.wear.input)
    implementation("androidx.wear:wear-remote-interactions:1.2.0")
    implementation(libs.health.services)
    implementation(libs.play.services.wearable)
    implementation(libs.play.services.coroutines)
    implementation(libs.coroutines.guava)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(libs.timber)
    implementation(libs.coil)
    coreLibraryDesugaring(libs.desugaring)
}
