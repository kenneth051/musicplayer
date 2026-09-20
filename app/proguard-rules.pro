# 1. COMPLETELY PROTECT YOUR APP CODE
-keep class com.example.musicplayer.** { *; }
-keep interface com.example.musicplayer.** { *; }
-keep enum com.example.musicplayer.** { *; }

# 2. PROTECT ALL ANDROIDX & JETPACK (The safest way to avoid launch crashes)
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# 3. PROTECT GOOGLE SERVICES & ADS
-keep class com.google.android.gms.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.**

# 4. PROTECT DATA PERSISTENCE (GSON)
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# 5. PROTECT MEDIA3 & AUDIO
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# 6. DISABLE OPTIMIZATIONS (The "Nuclear Option" for stability)
# If the app still crashes, disabling optimizations ensures the logic isn't "rewritten"
-dontoptimize
-dontobfuscate
