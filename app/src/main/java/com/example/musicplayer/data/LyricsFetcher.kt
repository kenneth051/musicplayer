package com.example.musicplayer.data

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
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
        try {
            val cleanTitle = cleanSearchTerm(title)
            val cleanArtist = cleanSearchTerm(artist)

            // 1. Try exact match API
            val exactUrl = "https://lrclib.net/api/get?track_name=${Uri.encode(cleanTitle)}&artist_name=${Uri.encode(cleanArtist)}"
            val exactResult = httpGet(exactUrl)
            if (exactResult != null) {
                val resp = gson.fromJson(exactResult, LrcLibResponse::class.java)
                val lyrics = resp?.syncedLyrics?.takeIf { it.isNotBlank() } ?: resp?.plainLyrics?.takeIf { it.isNotBlank() }
                if (lyrics != null) return@withContext lyrics
            }

            // 2. Try search API if exact match returned nothing
            val searchUrl = "https://lrclib.net/api/search?q=${Uri.encode("$cleanTitle $cleanArtist")}"
            val searchResult = httpGet(searchUrl)
            if (searchResult != null) {
                val results = gson.fromJson(searchResult, Array<LrcLibResponse>::class.java)
                if (!results.isNullOrEmpty()) {
                    val bestMatch = results.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: results.firstOrNull()
                    val lyrics = bestMatch?.syncedLyrics?.takeIf { it.isNotBlank() } ?: bestMatch?.plainLyrics?.takeIf { it.isNotBlank() }
                    if (lyrics != null) return@withContext lyrics
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("""(?i)\.(mp3|m4a|wav|flac|aac)"""), "")
            .replace(Regex("""(?i)\(official.*?\)|\[official.*?]"""), "")
            .replace(Regex("""(?i)\(lyric.*?\)|\[lyric.*?]"""), "")
            .trim()
    }

    private fun httpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "MusicPlayer/2.4")
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
