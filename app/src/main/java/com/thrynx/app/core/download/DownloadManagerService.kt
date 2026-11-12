package com.thrynx.app.core.download

import android.content.Context
import com.thrynx.app.core.database.DownloadDao
import com.thrynx.app.core.database.DownloadEntity
import com.thrynx.app.core.model.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DownloadManagerService(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()

    suspend fun enqueueDownload(id: String, title: String, url: String, targetFolder: File) {
        val fileName = "${title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")}.mp4"
        val destinationFile = File(targetFolder, fileName)

        val entity = DownloadEntity(
            id = id,
            title = title,
            sourceUrl = url,
            destinationPath = destinationFile.absolutePath,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            state = DownloadState.PENDING,
            mimeType = "video/mp4",
            timestamp = System.currentTimeMillis()
        )
        downloadDao.upsert(entity)
        startDownload(entity, destinationFile)
    }

    suspend fun startDownload(entity: DownloadEntity, destinationFile: File) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            downloadDao.upsert(entity.copy(state = DownloadState.RUNNING))

            val downloadUrl = URL(entity.sourceUrl)
            connection = downloadUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"

            val existingBytes = if (destinationFile.exists()) destinationFile.length() else 0L
            if (existingBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }

            connection.connect()
            val responseCode = connection.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                downloadDao.upsert(entity.copy(state = DownloadState.FAILED, errorMessage = "HTTP error $responseCode"))
                return@withContext
            }

            val contentLength = connection.contentLengthLong
            val totalLength = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                existingBytes + contentLength
            } else {
                contentLength
            }

            inputStream = connection.inputStream
            outputStream = FileOutputStream(destinationFile, responseCode == HttpURLConnection.HTTP_PARTIAL)

            val buffer = ByteArray(8192)
            var bytesRead: Int = 0
            var currentDownloaded = existingBytes
            var lastUpdate = System.currentTimeMillis()

            while (isActive && inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                currentDownloaded += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastUpdate > 500) {
                    lastUpdate = now
                    downloadDao.upsert(
                        entity.copy(
                            bytesDownloaded = currentDownloaded,
                            totalBytes = totalLength,
                            state = DownloadState.RUNNING
                        )
                    )
                }
            }

            if (isActive) {
                downloadDao.upsert(
                    entity.copy(
                        bytesDownloaded = currentDownloaded,
                        totalBytes = totalLength,
                        state = DownloadState.COMPLETED
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            downloadDao.upsert(entity.copy(state = DownloadState.FAILED, errorMessage = e.message))
        } finally {
            inputStream?.close()
            outputStream?.flush()
            outputStream?.close()
            connection?.disconnect()
        }
    }

    suspend fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val download = downloadDao.getDownloadById(id) ?: return
        downloadDao.upsert(download.copy(state = DownloadState.PAUSED))
    }

    suspend fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val download = downloadDao.getDownloadById(id) ?: return
        File(download.destinationPath).delete()
        downloadDao.deleteById(id)
    }
}
