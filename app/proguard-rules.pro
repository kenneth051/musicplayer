# Gson: Prevent obfuscation of Generic types and model classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.example.musicplayer.data.Song { *; }
-keep class com.example.musicplayer.data.Playlist { *; }

# Media3: Prevent obfuscation of Service and Player components
# The PlaybackService is referenced by class name in the Manifest and during session binding.
-keep class com.example.musicplayer.service.PlaybackService { *; }
-keep class com.example.musicplayer.service.SmartForwardingPlayer { *; }

# Prevent stripping of Media3 internal components that use reflection
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }

# Google Play Services (Ads)
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# Coil: Image loading
-keep class coil.** { *; }

# Palette API
-keep class androidx.palette.graphics.** { *; }

# General optimization rules
-dontwarn com.google.android.gms.ads.**
-dontwarn androidx.media3.**
