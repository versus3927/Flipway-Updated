plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.flipway.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.flipway.game"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "2.3-Textures"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Никаких библиотек: вся графика — Canvas, звук синтезируется кодом.
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")

    // логика игры не зависит от Android и проверяется обычными JVM-тестами
    testImplementation("junit:junit:4.13.2")
}
