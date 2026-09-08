package com.example.musicplayer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicPersistenceTest {

    private lateinit var persistence: MusicPersistence
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        persistence = MusicPersistence(context)
        // Note: Real DataStore persists across tests unless we use a unique name or clean up.
        // For a production-ready test, we'd use a temporary file.
    }

    @Test
    fun saveAndLoadFavorites() = runBlocking {
        val favoriteIds = setOf(1L, 2L, 3L)
        persistence.saveFavorites(favoriteIds)
        
        val loaded = persistence.favorites.first()
        assertEquals(favoriteIds, loaded)
    }

    @Test
    fun saveAndLoadPlaylists() = runBlocking {
        val testPlaylists = listOf(
            Playlist("1", "Workout", listOf(10L, 20L)),
            Playlist("2", "Chill", listOf(30L))
        )
        persistence.savePlaylists(testPlaylists)
        
        val loaded = persistence.playlists.first()
        assertEquals(2, loaded.size)
        assertEquals("Workout", loaded[0].name)
        assertEquals(listOf(10L, 20L), loaded[0].songIds)
    }

    @Test
    fun saveAndLoadRecentlyPlayed() = runBlocking {
        val ids = listOf(5L, 4L, 3L)
        persistence.saveRecentlyPlayed(ids)
        
        val loaded = persistence.recentlyPlayed.first()
        assertEquals(ids, loaded)
    }
}
