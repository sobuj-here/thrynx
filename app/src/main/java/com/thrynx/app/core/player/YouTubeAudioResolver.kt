package com.thrynx.app.core.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class YouTubeAudioStream(
    val videoId: String,
    val streamUrl: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val headers: Map<String, String>,
)

data class YouTubeVideoStream(
    val videoId: String,
    val streamUrl: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val qualityLabel: String,
    val headers: Map<String, String>,
)

data class YouTubeSearchTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationText: String,
    val thumbnailUrl: String
)

object YouTubeAudioResolver {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val VIDEO_ID_PATTERN = Pattern.compile(
        "(?:v=|youtu\\.be/|embed/|shorts/|music\\.youtube\\.com/watch\\?v=|^)([A-Za-z0-9_-]{11})"
    )

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("[A-Za-z0-9_-]{11}"))) {
            return trimmed
        }
        val matcher = VIDEO_ID_PATTERN.matcher(trimmed)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    fun isYouTubeUrl(input: String): Boolean {
        val lower = input.trim().lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be") || extractVideoId(input) != null
    }

    suspend fun resolveAudio(input: String): Result<YouTubeAudioStream> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(input)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL or Video ID: $input"))

        // Strategy 1: ANDROID_MUSIC client
        val androidMusicResult = fetchInnertubeAudioStream(
            videoId = videoId,
            clientName = "ANDROID_MUSIC",
            clientVersion = "8.39.42",
            userAgent = "com.google.android.apps.youtube.music/8.39.42 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip",
            origin = "https://music.youtube.com"
        )
        if (androidMusicResult != null) return@withContext Result.success(androidMusicResult)

        // Strategy 2: IOS client
        val iosResult = fetchInnertubeAudioStream(
            videoId = videoId,
            clientName = "IOS",
            clientVersion = "21.26.4",
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            origin = null
        )
        if (iosResult != null) return@withContext Result.success(iosResult)

        // Strategy 3: ANDROID client
        val androidResult = fetchInnertubeAudioStream(
            videoId = videoId,
            clientName = "ANDROID",
            clientVersion = "21.26.364",
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip",
            origin = null
        )
        if (androidResult != null) return@withContext Result.success(androidResult)

        Result.failure(IllegalStateException("Could not resolve playable audio stream for video: $videoId"))
    }

    suspend fun resolveVideo(input: String): Result<YouTubeVideoStream> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(input)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL or Video ID: $input"))

        // Strategy 1: ANDROID client (standard YouTube app returns direct video formats)
        val androidResult = fetchInnertubeVideoStream(
            videoId = videoId,
            clientName = "ANDROID",
            clientVersion = "21.26.364",
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip",
            origin = null
        )
        if (androidResult != null) return@withContext Result.success(androidResult)

        // Strategy 2: IOS client
        val iosResult = fetchInnertubeVideoStream(
            videoId = videoId,
            clientName = "IOS",
            clientVersion = "21.26.4",
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            origin = null
        )
        if (iosResult != null) return@withContext Result.success(iosResult)

        // Strategy 3: ANDROID_MUSIC client
        val musicResult = fetchInnertubeVideoStream(
            videoId = videoId,
            clientName = "ANDROID_MUSIC",
            clientVersion = "8.39.42",
            userAgent = "com.google.android.apps.youtube.music/8.39.42 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip",
            origin = "https://music.youtube.com"
        )
        if (musicResult != null) return@withContext Result.success(musicResult)

        Result.failure(IllegalStateException("Could not resolve playable video stream for: $videoId"))
    }

    private fun fetchInnertubeAudioStream(
        videoId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        origin: String?,
    ): YouTubeAudioStream? {
        try {
            val root = executePlayerRequest(videoId, clientName, clientVersion, userAgent, origin) ?: return null
            val videoDetails = root.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title") ?: "YouTube Audio"
            val artist = videoDetails?.optString("author") ?: "YouTube"
            val lengthSeconds = videoDetails?.optString("lengthSeconds")?.toLongOrNull() ?: 0L
            val durationMs = lengthSeconds * 1000L

            var artworkUrl: String? = null
            val thumbnailObj = videoDetails?.optJSONObject("thumbnail")
            val thumbnailsArray = thumbnailObj?.optJSONArray("thumbnails")
            if (thumbnailsArray != null && thumbnailsArray.length() > 0) {
                artworkUrl = thumbnailsArray.getJSONObject(thumbnailsArray.length() - 1).optString("url")
            }

            val streamingData = root.optJSONObject("streamingData") ?: return null
            val formats = streamingData.optJSONArray("adaptiveFormats") ?: return null

            var bestUrl: String? = null
            var bestBitrate = 0

            for (i in 0 until formats.length()) {
                val format = formats.getJSONObject(i)
                val mimeType = format.optString("mimeType")
                if (!mimeType.startsWith("audio/")) continue

                val url = format.optString("url")
                if (url.isBlank()) continue

                val bitrate = format.optInt("bitrate", 0)
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate
                    bestUrl = url
                }
            }

            if (bestUrl != null) {
                val playerClient = PlayerClient.forStreamUrl(bestUrl)
                return YouTubeAudioStream(
                    videoId = videoId,
                    streamUrl = bestUrl,
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    durationMs = durationMs,
                    headers = playerClient.mediaHeaders()
                )
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun fetchInnertubeVideoStream(
        videoId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        origin: String?,
    ): YouTubeVideoStream? {
        try {
            val root = executePlayerRequest(videoId, clientName, clientVersion, userAgent, origin) ?: return null
            val videoDetails = root.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title") ?: "YouTube Video"
            val artist = videoDetails?.optString("author") ?: "YouTube"
            val lengthSeconds = videoDetails?.optString("lengthSeconds")?.toLongOrNull() ?: 0L
            val durationMs = lengthSeconds * 1000L

            var artworkUrl: String? = null
            val thumbnailObj = videoDetails?.optJSONObject("thumbnail")
            val thumbnailsArray = thumbnailObj?.optJSONArray("thumbnails")
            if (thumbnailsArray != null && thumbnailsArray.length() > 0) {
                artworkUrl = thumbnailsArray.getJSONObject(thumbnailsArray.length() - 1).optString("url")
            }

            val streamingData = root.optJSONObject("streamingData") ?: return null

            // First check combined formats (video + audio in single MP4 stream, e.g. 720p/360p)
            val formats = streamingData.optJSONArray("formats")
            var bestUrl: String? = null
            var bestWidth = 0
            var bestHeight = 0
            var bestQuality = "HD"

            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val format = formats.getJSONObject(i)
                    val url = format.optString("url")
                    if (url.isBlank()) continue

                    val height = format.optInt("height", 0)
                    if (height > bestHeight) {
                        bestHeight = height
                        bestWidth = format.optInt("width", 0)
                        bestQuality = format.optString("qualityLabel", "${height}p")
                        bestUrl = url
                    }
                }
            }

            // Fallback to adaptive video format if combined is not available
            if (bestUrl == null) {
                val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
                if (adaptiveFormats != null) {
                    for (i in 0 until adaptiveFormats.length()) {
                        val format = adaptiveFormats.getJSONObject(i)
                        val mimeType = format.optString("mimeType")
                        if (!mimeType.startsWith("video/")) continue

                        val url = format.optString("url")
                        if (url.isBlank()) continue

                        val height = format.optInt("height", 0)
                        if (height > bestHeight) {
                            bestHeight = height
                            bestWidth = format.optInt("width", 0)
                            bestQuality = format.optString("qualityLabel", "${height}p")
                            bestUrl = url
                        }
                    }
                }
            }

            if (bestUrl != null) {
                val playerClient = PlayerClient.forStreamUrl(bestUrl)
                return YouTubeVideoStream(
                    videoId = videoId,
                    streamUrl = bestUrl,
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    durationMs = durationMs,
                    width = bestWidth,
                    height = bestHeight,
                    qualityLabel = bestQuality,
                    headers = playerClient.mediaHeaders()
                )
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun executePlayerRequest(
        videoId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        origin: String?
    ): JSONObject? {
        val jsonBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", clientName)
                    put("clientVersion", clientVersion)
                    put("hl", "en")
                    put("gl", "US")
                    if (clientName == "ANDROID_MUSIC" || clientName == "ANDROID") {
                        put("androidSdkVersion", 34)
                    } else if (clientName == "IOS") {
                        put("deviceModel", "iPhone16,2")
                    }
                })
            })
            put("videoId", videoId)
        }

        val requestBuilder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/player")
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/json")
        if (origin != null) {
            requestBuilder.header("Origin", origin)
            requestBuilder.header("Referer", "$origin/")
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        requestBuilder.post(body)

        val response = httpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) return null
        val responseString = response.body?.string() ?: return null
        return JSONObject(responseString)
    }

    suspend fun getTrendingTracks(): List<YouTubeSearchTrack> = withContext(Dispatchers.IO) {
        val hits = searchTracks("Top Songs 2024")
        if (hits.isNotEmpty()) hits else searchTracks("Trending Hits")
    }

    suspend fun searchTracks(query: String): List<YouTubeSearchTrack> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        try {
            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", trimmed)
            }

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val raw = response.body?.string() ?: return@withContext emptyList()
            parseSearchJson(JSONObject(raw))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSearchJson(root: JSONObject): List<YouTubeSearchTrack> {
        val results = mutableListOf<YouTubeSearchTrack>()
        val seenIds = mutableSetOf<String>()

        fun traverse(obj: Any?) {
            when (obj) {
                is JSONObject -> {
                    if (obj.has("musicResponsiveListItemRenderer")) {
                        val item = obj.getJSONObject("musicResponsiveListItemRenderer")
                        var videoId: String? = null
                        var title = ""
                        var subtitle = ""

                        val flex = item.optJSONArray("flexColumns")
                        if (flex != null && flex.length() > 0) {
                            val runs0 = flex.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                            if (runs0 != null && runs0.length() > 0) {
                                val firstRun = runs0.getJSONObject(0)
                                title = firstRun.optString("text")
                                val watchEndpoint = firstRun.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
                                if (watchEndpoint != null && watchEndpoint.has("videoId")) {
                                    videoId = watchEndpoint.optString("videoId")
                                }
                            }
                        }

                        if (flex != null && flex.length() > 1) {
                            val runs1 = flex.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                            if (runs1 != null) {
                                val sb = StringBuilder()
                                for (i in 0 until runs1.length()) {
                                    sb.append(runs1.getJSONObject(i).optString("text"))
                                }
                                subtitle = sb.toString()
                            }
                        }

                        if (videoId.isNullOrBlank()) {
                            val overlayNav = item.optJSONObject("overlay")
                                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                                ?.optJSONObject("content")
                                ?.optJSONObject("musicPlayButtonRenderer")
                                ?.optJSONObject("playNavigationEndpoint")
                                ?.optJSONObject("watchEndpoint")
                            if (overlayNav != null && overlayNav.has("videoId")) {
                                videoId = overlayNav.optString("videoId")
                            }
                        }

                        if (videoId.isNullOrBlank()) {
                            val plData = item.optJSONObject("playlistItemData")
                            if (plData != null && plData.has("videoId")) {
                                videoId = plData.optString("videoId")
                            }
                        }

                        var thumb = ""
                        val thumbnails = item.optJSONObject("thumbnail")
                            ?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")
                            ?.optJSONArray("thumbnails")
                        if (thumbnails != null && thumbnails.length() > 0) {
                            thumb = thumbnails.getJSONObject(thumbnails.length() - 1).optString("url")
                        }

                        if (!videoId.isNullOrBlank() && title.isNotBlank() && seenIds.add(videoId)) {
                            results.add(
                                YouTubeSearchTrack(
                                    videoId = videoId,
                                    title = title,
                                    artist = subtitle.ifBlank { "YouTube Music" },
                                    durationText = "",
                                    thumbnailUrl = thumb
                                )
                            )
                        }
                    } else {
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            traverse(obj.get(keys.next()))
                        }
                    }
                }
                is org.json.JSONArray -> {
                    for (i in 0 until obj.length()) {
                        traverse(obj.get(i))
                    }
                }
            }
        }

        traverse(root)
        return results
    }
}
