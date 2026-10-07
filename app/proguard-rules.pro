# ====================================================================
# PROGUARD / R8 RULES FOR VIBE MUSIC PLAYER
# ====================================================================

# 1. KEEP DATA MODELS FOR GSON SERIALIZATION
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.example.musicplayer.data.** { *; }

# 2. GSON RULES
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# 3. MEDIA3 & EXOPLAYER RULES
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# 4. GOOGLE PLAY SERVICES & ADMOB
-keep class com.google.android.gms.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.**

# 5. COIL & COROUTINES
-keep class coil.** { *; }
-dontwarn coil.**
-keep class kotlinx.coroutines.** { *; }

# 6. KEEP VIEWMODEL CONSTRUCTORS
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
