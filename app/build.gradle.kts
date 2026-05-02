plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    kotlin("plugin.serialization") version "2.2.0"
}

android {
    namespace = "com.example.yap"
    compileSdk {
        version = release(36)
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    defaultConfig {
        applicationId = "com.example.yap"
        minSdk = 25
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("io.coil-kt:coil-compose:2.5.0")

    // BOM позволяет не указывать версию для каждого отдельного модуля
    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))

    // Модуль для работы с хранилищем
    implementation("io.github.jan-tennert.supabase:storage-kt")

    // Ktor Client (обязателен, так как Supabase-kt работает на нем)
    // Версия Ktor должна быть 3.0.0 или выше для Supabase 3.x
    implementation("io.ktor:ktor-client-android:3.0.0")
    implementation("io.ktor:ktor-client-okhttp:3.0.0") // Версия должна совпадать с твоим Ktor

    implementation("androidx.credentials:credentials:1.2.2")
    // Дополнение для работы с Google ID
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    // Google ID библиотечка для упрощенного парсинга
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))

    // Библиотека для работы с Firestore (база данных)
    implementation("com.google.firebase:firebase-firestore")

    // Библиотека для Google Auth (если планируешь вход через Google)
    implementation("com.google.firebase:firebase-auth")

    implementation("com.google.firebase:firebase-config")


    implementation("com.google.firebase:firebase-messaging:25.0.1")

    // Библиотека для Cloud Storage (если будем загружать аудиофайлы)
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")

    // ОЧЕНЬ ВАЖНО: Поддержка Coroutines для Firebase
    // Позволяет писать val result = query.get().await() вместо колбэков
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // 1. Принудительно ставим 1.3.0 ПЕРЕД остальными для LoadingIndicator
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material3:material3-android:1.3.0")

    // 2. Твой BOM и базовые библиотеки (возвращаем для структуры проекта)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // 3. Остальные компоненты
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    // 4. Твои кастомные зависимости (Retrofit, DataStore и т.д.)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation(libs.androidx.compose.foundation)
    implementation(libs.googleid)
    implementation(libs.androidx.compose.remote.creation.core)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.google.firebase.crashlytics.buildtools)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.compose.ui.text)


    // Тесты и дебаг
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}