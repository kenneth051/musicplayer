# Gson uses generic type information stored in a class file when working with fields.
# R8 removes such information by default, so it must be kept explicitly - without this,
# the TypeToken<Set<Long>>/TypeToken<List<Long>>/TypeToken<Map<Long, Int>> usages in
# MusicPersistence would deserialize incorrectly (or crash) in a release build.
-keepattributes Signature
-keepattributes *Annotation*

-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Favorites/playlists/recently-played/play-counts are all Gson-serialized to DataStore by
# field name; keep these model classes intact so persisted JSON keeps round-tripping after
# a release build renames/strips "unused-looking" fields.
-keep class com.example.musicplayer.data.Song { *; }
-keep class com.example.musicplayer.data.Playlist { *; }
