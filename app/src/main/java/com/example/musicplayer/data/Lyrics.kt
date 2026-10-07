package com.example.musicplayer.data

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

object LrcParser {
    private val LRC_REGEX = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

    fun parse(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        content.lines().forEach { line ->
            val match = LRC_REGEX.find(line.trim())
            if (match != null) {
                val (minStr, secStr, fracStr, text) = match.destructured
                val minMs = minStr.toLong() * 60 * 1000
                val secMs = secStr.toLong() * 1000
                val fracMs = if (fracStr.length == 2) fracStr.toLong() * 10 else fracStr.toLong()
                val totalMs = minMs + secMs + fracMs
                if (text.isNotBlank()) {
                    lines.add(LyricLine(totalMs, text.trim()))
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    fun isLrcFormat(content: String): Boolean {
        return content.lines().any { LRC_REGEX.containsMatchIn(it) }
    }

    fun getActiveIndex(lyrics: List<LyricLine>, positionMs: Long, offsetMs: Long = 300L): Int {
        if (lyrics.isEmpty()) return -1
        val effectivePosition = positionMs + offsetMs
        var active = -1
        for (i in lyrics.indices) {
            if (effectivePosition >= lyrics[i].timestampMs) {
                active = i
            } else {
                break
            }
        }
        return active
    }
}
