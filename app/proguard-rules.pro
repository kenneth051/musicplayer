# ====================================================================
# PROGUARD / R8 RULES FOR VIBE MUSIC PLAYER
# ====================================================================

# 1. PROTECT ALL APP CLASSES, ACTIVITIES, SERVICES AND VIEWMODELS
-keep class com.example.musicplayer.** { *; }
-keep interface com.example.musicplayer.** { *; }
-keep enum com.example.musicplayer.** { *; }

# 2. PROTECT ANDROIDX & JETPACK COMPOSE
-keep class androidx.** { *; }
-dontwarn androidx.**

# 3. PROTECT DATA PERSISTENCE & GSON
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# 4. PROTECT MEDIA3 & EXOPLAYER
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# 5. PROTECT GOOGLE PLAY SERVICES & ADMOB
-keep class com.google.android.gms.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.**

# 6. PROTECT COIL & COROUTINES
-keep class coil.** { *; }
-dontwarn coil.**
-keep class kotlinx.coroutines.** { *; }
