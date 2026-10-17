# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# signingConfig.enableV1Signing, signingConfig.enableV2Signing, and
# signingConfig.enableSourceStamp flags in build.gradle.

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

# Keep Room database classes
-keep class androidx.room.** { *; }
-keepclassmembers class androidx.room.** { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }
-keepclassmembers class androidx.datastore.** { *; }

# Keep our app classes
-keep class com.womensafety.app.** { *; }
-keepclassmembers class com.womensafety.app.** { *; }
