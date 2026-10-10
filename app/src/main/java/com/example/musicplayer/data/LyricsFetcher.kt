package com.example.musicplayer.data

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class LrcLibResponse(
    @SerializedName("id") val id: Long?,
    @SerializedName("trackName") val trackName: String?,
    @SerializedName("artistName") val artistName: String?,
    @SerializedName("plainLyrics") val plainLyrics: String?,
    @SerializedName("syncedLyrics") val syncedLyrics: String?
)

object LyricsFetcher {
    private val gson = Gson()

    suspend fun fetchLyrics(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSearchTerm(title)
        val cleanArtist = cleanSearchTerm(artist)

        // Try up to 3 automatic retries to handle transient server/network drops
        for (attempt in 1..3) {
            try {
                // 1. Try exact match API if artist is known
                if (cleanArtist.isNotBlank() && cleanArtist.lowercase() != "unknown") {
                    val exactUrl = "https://lrclib.net/api/get?track_name=${Uri.encode(cleanTitle)}&artist_name=${Uri.encode(cleanArtist)}"
                    val exactResult = httpGet(exactUrl)
                    if (exactResult != null) {
                        val resp = gson.fromJson(exactResult, LrcLibResponse::class.java)
                        val lyrics = resp?.syncedLyrics?.takeIf { it.isNotBlank() } ?: resp?.plainLyrics?.takeIf { it.isNotBlank() }
                        if (lyrics != null) return@withContext lyrics
                    }
                }

                // 2. Try search API with title + artist
                val searchQuery = if (cleanArtist.isNotBlank() && cleanArtist.lowercase() != "unknown") {
                    "$cleanTitle $cleanArtist"
                } else {
                    cleanTitle
                }
                val searchUrl = "https://lrclib.net/api/search?q=${Uri.encode(searchQuery)}"
                val searchResult = httpGet(searchUrl)
                if (searchResult != null) {
                    val results = gson.fromJson(searchResult, Array<LrcLibResponse>::class.java)
                    if (!results.isNullOrEmpty()) {
                        val bestMatch = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: results.firstOrNull()
                        val lyrics = bestMatch?.syncedLyrics?.takeIf { it.isNotBlank() } ?: bestMatch?.plainLyrics?.takeIf { it.isNotBlank() }
                        if (lyrics != null) return@withContext lyrics
                    }
                }

                // 3. Fallback: title-only search if title contains extra tags or artist is unknown
                if (cleanArtist.lowercase() == "unknown" || searchResult == null) {
                    val titleOnlyUrl = "https://lrclib.net/api/search?q=${Uri.encode(cleanTitle)}"
                    val titleResult = httpGet(titleOnlyUrl)
                    if (titleResult != null) {
                        val results = gson.fromJson(titleResult, Array<LrcLibResponse>::class.java)
                        if (!results.isNullOrEmpty()) {
                            val bestMatch = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: results.firstOrNull()
                            val lyrics = bestMatch?.syncedLyrics?.takeIf { it.isNotBlank() } ?: bestMatch?.plainLyrics?.takeIf { it.isNotBlank() }
                            if (lyrics != null) return@withContext lyrics
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Brief backoff before next attempt if network/server failed
            if (attempt < 3) delay(500L)
        }

        null
    }

    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("""(?i)\.(mp3|m4a|wav|flac|aac|ogg|opus)"""), "")
            .replace(Regex("""(?i)\(official.*?\)|\[official.*?]"""), "")
            .replace(Regex("""(?i)\(lyric.*?\)|\[lyric.*?]"""), "")
            .replace(Regex("""(?i)\(audio.*?\)|\[audio.*?]"""), "")
            .replace(Regex("""(?i)\(video.*?\)|\[video.*?]"""), "")
            .replace(Regex("""(?i)\(feat.*?\)|\[feat.*?]"""), "")
            .replace(Regex("""(?i)\(ft.*?\)|\[ft.*?]"""), "")
            .trim()
    }

    private fun httpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "MusicPlayer/3.0")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
