# ====================================================================
# PROGUARD / R8 RULES FOR VIBE MUSIC PLAYER
# ====================================================================

# 1. PROTECT APP CLASSES & DATA MODELS
-keep class com.example.musicplayer.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# 2. GSON RULES
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# 3. MEDIA3 RULES
-dontwarn androidx.media3.**
-dontwarn com.google.android.exoplayer2.**

# 4. ADMOB & PLAY SERVICES
-dontwarn com.google.android.gms.**

# 5. COIL & COROUTINES
-dontwarn coil.**
-keep class kotlinx.coroutines.** { *; }
