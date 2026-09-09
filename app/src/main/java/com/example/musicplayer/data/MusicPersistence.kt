package com.example.musicplayer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "music_prefs")

class MusicPersistence(private val context: Context) {
    private val gson = Gson()
    
    private val FAVORITES_KEY = stringPreferencesKey("favorites")
    private val PLAYLISTS_KEY = stringPreferencesKey("playlists")
    private val RECENTLY_PLAYED_KEY = stringPreferencesKey("recently_played")
    private val PLAY_COUNTS_KEY = stringPreferencesKey("play_counts")
    private val EXCLUDE_WHATSAPP_AUDIO_KEY = booleanPreferencesKey("exclude_whatsapp_audio")

    val favorites: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        val json = prefs[FAVORITES_KEY] ?: "[]"
        val type = object : TypeToken<Set<Long>>() {}.type
        gson.fromJson(json, type)
    }

    suspend fun saveFavorites(favorites: Set<Long>) {
        context.dataStore.edit { prefs ->
            prefs[FAVORITES_KEY] = gson.toJson(favorites)
        }
    }

    val playlists: Flow<List<Playlist>> = context.dataStore.data.map { prefs ->
        val json = prefs[PLAYLISTS_KEY] ?: "[]"
        val type = object : TypeToken<List<Playlist>>() {}.type
        gson.fromJson(json, type)
    }

    suspend fun savePlaylists(playlists: List<Playlist>) {
        context.dataStore.edit { prefs ->
            prefs[PLAYLISTS_KEY] = gson.toJson(playlists)
        }
    }

    val recentlyPlayed: Flow<List<Long>> = context.dataStore.data.map { prefs ->
        val json = prefs[RECENTLY_PLAYED_KEY] ?: "[]"
        val type = object : TypeToken<List<Long>>() {}.type
        gson.fromJson(json, type)
    }

    suspend fun saveRecentlyPlayed(songIds: List<Long>) {
        context.dataStore.edit { prefs ->
            prefs[RECENTLY_PLAYED_KEY] = gson.toJson(songIds)
        }
    }

    val playCounts: Flow<Map<Long, Int>> = context.dataStore.data.map { prefs ->
        val json = prefs[PLAY_COUNTS_KEY] ?: "{}"
        val type = object : TypeToken<Map<Long, Int>>() {}.type
        gson.fromJson(json, type)
    }

    suspend fun savePlayCounts(counts: Map<Long, Int>) {
        context.dataStore.edit { prefs ->
            prefs[PLAY_COUNTS_KEY] = gson.toJson(counts)
        }
    }

    val excludeWhatsAppAudio: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[EXCLUDE_WHATSAPP_AUDIO_KEY] ?: true
    }

    suspend fun saveExcludeWhatsAppAudio(exclude: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[EXCLUDE_WHATSAPP_AUDIO_KEY] = exclude
        }
    }
}
