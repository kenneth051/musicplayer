# ====================================================================
# PROGUARD / R8 SURGICAL RULES FOR VIBE MUSIC PLAYER
# ====================================================================

# 1. SPECIFIC CLASSES ACCESSED REFLECTIVELY BY ANDROID OS MANIFEST
-keep class com.example.musicplayer.MainActivity { *; }
-keep class com.example.musicplayer.service.PlaybackService { *; }

# 2. SPECIFIC VIEWMODELS ACCESSED REFLECTIVELY BY JETPACK
-keep class com.example.musicplayer.viewmodel.MusicViewModel { <init>(...); }

# 3. SPECIFIC DATA MODELS ACCESSED REFLECTIVELY BY GSON SERIALIZATION
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.example.musicplayer.data.Song { *; }
-keep class com.example.musicplayer.data.Playlist { *; }
-keep class com.example.musicplayer.data.Queue { *; }
-keep class com.example.musicplayer.data.QueueItem { *; }
-keep class com.example.musicplayer.data.LyricLine { *; }

# 4. GSON REFLECTION RULES
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# 5. MEDIA3 & SMART FORWARDING PLAYER
-keep class com.example.musicplayer.service.SmartForwardingPlayer { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# 6. ADMOB & PLAY SERVICES
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

# 7. COIL & COROUTINES
-keep class coil.** { *; }
-dontwarn coil.**
-keep class kotlinx.coroutines.** { *; }

# 8. FIREBASE & CRASHLYTICS
-keepattributes SourceFile,LineNumberTable
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# 9. ANDROIDX APP STARTUP, WORKMANAGER & ROOM DATABASE GENERATED IMPL CLASSES
-keep class androidx.startup.** { *; }
-keep class * implements androidx.startup.Initializer { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
-keep class **.*_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
