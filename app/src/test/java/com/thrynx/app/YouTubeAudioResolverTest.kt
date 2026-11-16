package com.thrynx.app

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.DeliveryMethod

class YouTubeAudioResolverTest {

    private class TestDownloader : Downloader() {
        private val client = OkHttpClient()

        override fun execute(request: Request): Response {
            val reqBuilder = okhttp3.Request.Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
            request.headers().forEach { (name, values) ->
                values.forEach { v -> reqBuilder.addHeader(name, v) }
            }
            if (request.headers()["User-Agent"] == null) {
                reqBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            val resp = client.newCall(reqBuilder.build()).execute()
            val body = resp.body?.string() ?: ""
            return Response(resp.code, resp.message, resp.headers.toMultimap(), body, resp.request.url.toString())
        }
    }

    @Test
    fun testExtraction() {
        NewPipe.init(TestDownloader())
        val videoId = "dQw4w9WgXcQ"
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        println("Title: ${extractor.name}")
        println("Uploader: ${extractor.uploaderName}")
        val audioStreams = extractor.audioStreams.filter {
            !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        println("Extracted audio streams count: ${audioStreams.size}")
        for (a in audioStreams) {
            println(" - Bitrate: ${a.averageBitrate} kbps | Format: ${a.format} | URL: ${a.content.take(60)}...")
        }
        assert(audioStreams.isNotEmpty())
    }
}
