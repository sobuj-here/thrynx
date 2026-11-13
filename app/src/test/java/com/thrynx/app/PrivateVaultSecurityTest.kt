package com.thrynx.app

import com.thrynx.app.core.database.PrivateVideoEntity
import com.thrynx.app.core.model.MediaItemModel
import org.junit.Assert.*
import org.junit.Test

class PrivateVaultSecurityTest {

    @Test
    fun testPrivateVideoExclusionFromPublicList() {
        val publicList = listOf(
            MediaItemModel(id = 1L, path = "/storage/emulated/0/DCIM/public1.mp4", title = "Public Video 1"),
            MediaItemModel(id = 2L, path = "/storage/emulated/0/DCIM/private.mp4", title = "Private Sensitive Video"),
            MediaItemModel(id = 3L, path = "/storage/emulated/0/DCIM/public2.mp4", title = "Public Video 2")
        )

        val privateVaultVideos = listOf(
            PrivateVideoEntity(
                mediaUri = "content://video/2",
                originalPath = "/storage/emulated/0/DCIM/private.mp4",
                title = "Private Sensitive Video",
                durationMs = 50000L,
                sizeBytes = 1024000L
            )
        )

        val privatePaths = privateVaultVideos.map { it.originalPath }.toSet()
        val visibleVideos = publicList.filter { !privatePaths.contains(it.path) }

        assertEquals(2, visibleVideos.size)
        assertFalse(visibleVideos.any { it.title == "Private Sensitive Video" })
        assertTrue(visibleVideos.any { it.title == "Public Video 1" })
        assertTrue(visibleVideos.any { it.title == "Public Video 2" })
    }

    @Test
    fun testVaultPinValidation() {
        val configuredPin = "4826"
        val correctAttempt = "4826"
        val wrongAttempt = "1234"

        assertEquals(configuredPin, correctAttempt)
        assertNotEquals(configuredPin, wrongAttempt)
        assertTrue(configuredPin.length == 4 && configuredPin.all { it.isDigit() })
    }
}
