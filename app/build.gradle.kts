plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.newsflash"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.newsflash"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // Biblioteca para programar y ejecutar tareas en segundo plano.
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    // Biblioteca de compatibilidad para las notificaciones de Android.
    implementation("androidx.core:core-ktx:1.19.1")
}