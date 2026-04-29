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

-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keep class com.example.yap.models.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# 2. GSON
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.stream.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}


# 3. FIREBASE & GOOGLE SERVICES
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# 4. SUPABASE, KTOR & OKHTTP
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, Exceptions
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# 5. KOTLIN SERIALIZATION
-keepclassmembers class ** {
    *** Companion;
}
-keepnames class kotlinx.serialization.internal.EnumSerializer
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# 6. COIL & CREDENTIALS
-keep class com.google.android.libraries.identity.googleid.** { *; }

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}