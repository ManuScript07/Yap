# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==============================================================================
# 1. ГЛОБАЛЬНЫЕ НАСТРОЙКИ (Критично для Metadata и Рефлексии)
# ==============================================================================
-keepattributes Signature, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, EnclosingMethod, InnerClasses, Exceptions

# Сохраняем интерфейс Continuation, чтобы suspend функции не ломались в рантайме
-keep class kotlin.coroutines.Continuation { *; }

# ==============================================================================
# 2. МОДЕЛИ ДАННЫХ И КОНКРЕТНЫЕ МОДЕЛИ
# ==============================================================================
# Исправлено: добавлена точка перед звездочками, чтобы захватить пакет полностью
-keep class com.example.yap.data.model.** { *; }

# Персональная защита для GroqResponse (на случай, если он вне общего пакета)
-keep class **.GroqResponse { *; }
-keepclassmembers class **.GroqResponse { *; }

# ==============================================================================
# 3. RETROFIT & OKHTTP
# ==============================================================================
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn javax.annotation.**

# Защита интерфейсов API и их методов с аннотациями
-keep @retrofit2.http.* interface * { *; }
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# ==============================================================================
# 4. GSON (Исправление ClassCastException)
# ==============================================================================
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.stream.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ==============================================================================
# 5. KOTLIN COROUTINES & SERIALIZATION
# ==============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

-keepclassmembers class ** {
    *** Companion;
}
-keepnames class kotlinx.serialization.internal.EnumSerializer
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# ==============================================================================
# 6. ВНЕШНИЕ СЕРВИСЫ И БИБЛИОТЕКИ (Firebase, Supabase, Coil)
# ==============================================================================
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn io.ktor.**

-keep class com.google.android.libraries.identity.googleid.** { *; }