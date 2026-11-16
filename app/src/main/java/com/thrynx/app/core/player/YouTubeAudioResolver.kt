package com.thrynx.app.core.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import org.schabi.newpipe.extractor.stream.DeliveryMethod
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

    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: NewPipeRequest): NewPipeResponse {
            val reqBuilder = okhttp3.Request.Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())

            request.headers().forEach { (name, values) ->
                values.forEach { v -> reqBuilder.addHeader(name, v) }
            }
            if (request.headers()["User-Agent"] == null) {
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            }

            val resp = client.newCall(reqBuilder.build()).execute()
            val body = resp.body?.string() ?: ""
            return NewPipeResponse(resp.code, resp.message, resp.headers.toMultimap(), body, resp.request.url.toString())
        }
    }

    private var isNewPipeInit = false

    @Synchronized
    private fun ensureNewPipe() {
        if (!isNewPipeInit) {
            try {
                NewPipe.init(OkHttpDownloader(httpClient))
                isNewPipeInit = true
            } catch (_: Exception) {
            }
        }
    }

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

        // Strategy 1: NewPipeExtractor engine with automatic JavaScript cipher deciphering
        try {
            ensureNewPipe()
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            val audioStreams = extractor.audioStreams.filter {
                !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }
            if (audioStreams.isNotEmpty()) {
                val bestAudio = audioStreams.maxByOrNull { it.averageBitrate } ?: audioStreams.first()
                val title = extractor.name?.ifBlank { "YouTube Audio" } ?: "YouTube Audio"
                val artist = extractor.uploaderName?.ifBlank { "YouTube" } ?: "YouTube"
                val artworkUrl = extractor.thumbnails?.lastOrNull()?.url
                val durationMs = extractor.length * 1000L

                return@withContext Result.success(
                    YouTubeAudioStream(
                        videoId = videoId,
                        streamUrl = bestAudio.content,
                        title = title,
                        artist = artist,
                        artworkUrl = artworkUrl,
                        durationMs = durationMs,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                        )
                    )
                )
            }
        } catch (_: Exception) {
        }

        // Strategy 2: High-speed ANDROID_VR Innertube client
        val vrResult = fetchVrAudioStream(videoId)
        if (vrResult != null) return@withContext Result.success(vrResult)

        // Strategy 3: ANDROID_MUSIC client fallback
        val musicResult = fetchInnertubeAudioStream(
            videoId = videoId,
            clientName = "ANDROID_MUSIC",
            clientVersion = "8.39.42",
            userAgent = "com.google.android.apps.youtube.music/8.39.42 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip"
        )
        if (musicResult != null) return@withContext Result.success(musicResult)

        Result.failure(IllegalStateException("Could not resolve playable audio stream for video: $videoId"))
    }

    suspend fun resolveVideo(input: String): Result<YouTubeVideoStream> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(input)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL or Video ID: $input"))

        // Strategy 1: NewPipeExtractor (muxed video+audio progressive HTTP streams)
        try {
            ensureNewPipe()
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()

            val videoStreams = extractor.videoStreams.filter {
                !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
            }
            if (videoStreams.isNotEmpty()) {
                val bestVideo = videoStreams.maxByOrNull { it.height } ?: videoStreams.first()
                val title = extractor.name?.ifBlank { "YouTube Video" } ?: "YouTube Video"
                val artist = extractor.uploaderName?.ifBlank { "YouTube" } ?: "YouTube"
                val artworkUrl = extractor.thumbnails?.lastOrNull()?.url
                val durationMs = extractor.length * 1000L

                return@withContext Result.success(
                    YouTubeVideoStream(
                        videoId = videoId,
                        streamUrl = bestVideo.content,
                        title = title,
                        artist = artist,
                        artworkUrl = artworkUrl,
                        durationMs = durationMs,
                        width = bestVideo.width,
                        height = bestVideo.height,
                        qualityLabel = "${bestVideo.height}p",
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                        )
                    )
                )
            }
        } catch (_: Exception) {
        }

        // Strategy 2: ANDROID_VR client direct video formats
        val vrResult = fetchVrVideoStream(videoId)
        if (vrResult != null) return@withContext Result.success(vrResult)

        Result.failure(IllegalStateException("Could not resolve playable video stream for: $videoId"))
    }

    private fun fetchVrAudioStream(videoId: String): YouTubeAudioStream? {
        try {
            val root = executePlayerRequest(
                videoId = videoId,
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                userAgent = "Mozilla/5.0 (Linux; Android 12; Quest Pro) AppleWebKit/537.36",
                deviceModel = "Quest Pro"
            ) ?: return null

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
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return null

            var bestUrl: String? = null
            var bestBitrate = 0

            for (i in 0 until adaptiveFormats.length()) {
                val format = adaptiveFormats.getJSONObject(i)
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
                return YouTubeAudioStream(
                    videoId = videoId,
                    streamUrl = bestUrl,
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    durationMs = durationMs,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 12; Quest Pro) AppleWebKit/537.36"
                    )
                )
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun fetchVrVideoStream(videoId: String): YouTubeVideoStream? {
        try {
            val root = executePlayerRequest(
                videoId = videoId,
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                userAgent = "Mozilla/5.0 (Linux; Android 12; Quest Pro) AppleWebKit/537.36",
                deviceModel = "Quest Pro"
            ) ?: return null

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

            // First check combined formats
            val formats = streamingData.optJSONArray("formats")
            var bestUrl: String? = null
            var bestWidth = 0
            var bestHeight = 0
            var bestQuality = "360p"

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

            // Fallback to adaptive video
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
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 12; Quest Pro) AppleWebKit/537.36"
                    )
                )
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun fetchInnertubeAudioStream(
        videoId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String
    ): YouTubeAudioStream? {
        try {
            val root = executePlayerRequest(videoId, clientName, clientVersion, userAgent, null) ?: return null
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
                return YouTubeAudioStream(
                    videoId = videoId,
                    streamUrl = bestUrl,
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    durationMs = durationMs,
                    headers = mapOf("User-Agent" to userAgent)
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
        deviceModel: String?
    ): JSONObject? {
        val clientObj = JSONObject().apply {
            put("clientName", clientName)
            put("clientVersion", clientVersion)
            put("hl", "en")
            put("gl", "US")
            if (deviceModel != null) {
                put("deviceModel", deviceModel)
                put("osName", "Android")
                put("osVersion", "12")
            }
        }

        val jsonBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", clientObj)
            })
            put("videoId", videoId)
            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("html5Preference", "HTML5_PREF_WANTS")
                    put("signatureTimestamp", 19800)
                })
            })
        }

        val requestBuilder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player")
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/json")

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
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Content-Type", "application/json")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val respBody = response.body?.string() ?: return@withContext emptyList()
            parseSearchResults(JSONObject(respBody))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSearchResults(json: JSONObject): List<YouTubeSearchTrack> {
        val results = mutableListOf<YouTubeSearchTrack>()
        try {
            val contents = json.optJSONObject("contents") ?: return emptyList()
            val tabbed = contents.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            val sectionList = tabbed ?: contents.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            if (sectionList == null) return emptyList()

            for (i in 0 until sectionList.length()) {
                val section = sectionList.optJSONObject(i) ?: continue
                val shelf = section.optJSONObject("musicShelfRenderer")
                    ?: section.optJSONObject("musicCardShelfRenderer")
                    ?: continue

                val items = shelf.optJSONArray("contents") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    val track = parseTrackItem(item)
                    if (track != null && results.none { it.videoId == track.videoId }) {
                        results.add(track)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    private fun parseTrackItem(item: JSONObject): YouTubeSearchTrack? {
        try {
            val flexColumns = item.optJSONArray("flexColumns") ?: return null
            if (flexColumns.length() < 2) return null

            val col0 = flexColumns.getJSONObject(0).optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val titleRuns = col0?.optJSONObject("text")?.optJSONArray("runs")
            val title = titleRuns?.optJSONObject(0)?.optString("text") ?: return null

            val col1 = flexColumns.getJSONObject(1).optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val artistRuns = col1?.optJSONObject("text")?.optJSONArray("runs")
            val artist = artistRuns?.optJSONObject(0)?.optString("text") ?: "YouTube"

            var durationText = ""
            if (artistRuns != null && artistRuns.length() > 2) {
                durationText = artistRuns.optJSONObject(artistRuns.length() - 1)?.optString("text") ?: ""
            }

            var videoId: String? = null
            val playNav = item.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")

            if (!playNav.isNullOrBlank()) {
                videoId = playNav
            }

            if (videoId == null) {
                val nav = item.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                if (!nav.isNullOrBlank()) videoId = nav
            }

            if (videoId == null && titleRuns != null) {
                for (r in 0 until titleRuns.length()) {
                    val run = titleRuns.getJSONObject(r)
                    val ep = run.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                    if (!ep.isNullOrBlank()) {
                        videoId = ep
                        break
                    }
                }
            }

            if (videoId.isNullOrBlank()) return null

            var thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val thumbRenderer = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
            val thumbs = thumbRenderer?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            if (thumbs != null && thumbs.length() > 0) {
                thumbUrl = thumbs.getJSONObject(thumbs.length() - 1).optString("url", thumbUrl)
            }

            return YouTubeSearchTrack(
                videoId = videoId,
                title = title,
                artist = artist,
                durationText = durationText,
                thumbnailUrl = thumbUrl
            )
        } catch (_: Exception) {
            return null
        }
    }
}
