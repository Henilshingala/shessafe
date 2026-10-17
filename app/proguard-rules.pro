# Women Safety App - ProGuard Rules for Release Build

# ===== KEEP ESSENTIAL CODE =====

# Keep data models (used by Room)
-keep class com.womensafety.app.data.models.** { *; }

# Keep Emergency entities (Room requires them)
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# Keep BroadcastReceivers (must be discoverable)
-keep class * extends android.content.BroadcastReceiver {
    public <init>(...);
}

# Keep Services (must be discoverable)
-keep class * extends android.app.Service {
    public <init>(...);
}

# Keep Activities (must be discoverable)
-keep class * extends androidx.activity.ComponentActivity {
    public <init>(...);
}

# ===== OBFUSCATE =====

# Obfuscate everything else
-repackageclasses ''
-allowaccessmodification

# ===== OPTIMIZATION =====

-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# ===== REMOVE DEBUG CODE =====

# Remove all Log calls in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Remove BuildConfig debug flags
-assumenosideeffects class com.womensafety.app.BuildConfig {
    public static final boolean DEBUG return false;
}

# ===== COMPOSE =====

-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# ===== ROOM =====

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ===== COROUTINES =====

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ===== KOTLIN =====

-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ===== ANDROID =====

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep View constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ===== SECURITY =====

# Don't obfuscate security-critical classes
-keep class com.womensafety.app.data.SecurePreferences { *; }
-keep class com.womensafety.app.data.SimpleOTPManager { *; }

# Remove stack traces in production
-keepattributes !SourceFile,!LineNumberTable

# Don't print notes about reflection
-dontnote **

# Don't warn about missing classes
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
