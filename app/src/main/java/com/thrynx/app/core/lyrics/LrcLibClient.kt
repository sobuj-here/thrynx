package com.thrynx.app.core.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Clean LRCLIB client for fetching synced lyrics.
 * Branded for THRYNX.
 */
object LrcLibClient {

    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "THRYNX (https://github.com/sobuj-here/thrynx)"
    private val STAMP_REGEX = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")

    suspend fun fetchLyrics(title: String, artist: String, durationMs: Long): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            val cleanTitle = cleanMetadata(title)
            val cleanArtist = cleanMetadata(artist)
            val seconds = (durationMs / 1000).toInt()

            val hasValidArtist = cleanArtist.isNotBlank() && 
                    cleanArtist != "Audio Track" && 
                    cleanArtist != "THRYNX Audio" && 
                    cleanArtist != "<unknown>"

            val exact = if (hasValidArtist) {
                runCatching { exactMatch(cleanTitle, cleanArtist, seconds) }.getOrNull()
            } else null

            val synced = exact ?: runCatching { searchMatch(cleanTitle, cleanArtist, seconds) }.getOrNull()
            synced?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
        }

    private fun exactMatch(title: String, artist: String, seconds: Int): String? {
        val query = "track_name=${encode(title)}&artist_name=${encode(artist)}&duration=$seconds"
        val response = get("$BASE_URL/get?$query") ?: return null
        return try {
            val json = JSONObject(response)
            json.optString("syncedLyrics").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun searchMatch(title: String, artist: String, seconds: Int): String? {
        val hasValidArtist = artist.isNotBlank() && 
                artist != "Audio Track" && 
                artist != "THRYNX Audio" && 
                artist != "<unknown>"

        val query = if (hasValidArtist) {
            "track_name=${encode(title)}&artist_name=${encode(artist)}"
        } else {
            "track_name=${encode(title)}"
        }
        val response = get("$BASE_URL/search?$query") ?: return null
        return try {
            val array = JSONArray(response)
            var bestHit: String? = null
            var minDiff = Int.MAX_VALUE
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val synced = item.optString("syncedLyrics")
                if (synced.isNotBlank()) {
                    val dur = item.optInt("duration", 0)
                    val diff = abs(dur - seconds)
                    if (diff < minDiff) {
                        minDiff = diff
                        bestHit = synced
                    }
                }
            }
            bestHit
        } catch (e: Exception) {
            null
        }
    }

    private fun get(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 6000
                readTimeout = 6000
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseLrc(lrc: String): List<LyricLine> {
        val rawLines = mutableListOf<Pair<Long, String>>()
        for (line in lrc.lines()) {
            val match = STAMP_REGEX.find(line.trim()) ?: continue
            val (minStr, secStr, fracStr, text) = match.destructured
            val min = minStr.toLongOrNull() ?: 0L
            val sec = secStr.toLongOrNull() ?: 0L
            val fracMs = when (fracStr.length) {
                2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                3 -> fracStr.toLongOrNull() ?: 0L
                else -> 0L
            }
            val startMs = min * 60_000L + sec * 1_000L + fracMs
            rawLines.add(startMs to text.trim())
        }

        if (rawLines.isEmpty()) return emptyList()
        rawLines.sortBy { it.first }

        val result = mutableListOf<LyricLine>()
        for (i in 0 until rawLines.size) {
            val current = rawLines[i]
            val nextStart = if (i + 1 < rawLines.size) rawLines[i + 1].first else current.first + 6000L
            val lineText = current.second
            if (lineText.isNotBlank()) {
                val words = lineText.split(Regex("\\s+")).filter { it.isNotBlank() }
                val wordDuration = if (words.isNotEmpty()) (nextStart - current.first) / words.size else 0L
                val wordList = words.mapIndexed { wIdx, wText ->
                    LyricWord(
                        startMs = current.first + wIdx * wordDuration,
                        endMs = current.first + (wIdx + 1) * wordDuration,
                        text = wText
                    )
                }
                result.add(
                    LyricLine(
                        startMs = current.first,
                        endMs = nextStart,
                        text = lineText,
                        words = wordList
                    )
                )
            }
        }
        return result
    }

    private fun cleanMetadata(text: String): String {
        return text.replace(Regex("""\s*[\(\[\{].*?[\)\]\}]"""), "")
            .replace(Regex("""(?i)\b(feat|ft|official|audio|video|lyrics?)\b.*"""), "")
            .trim()
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
