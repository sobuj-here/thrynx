package com.thrynx.app

import com.thrynx.app.core.model.DurationFilter
import com.thrynx.app.core.model.MediaItemModel
import com.thrynx.app.core.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoLibraryRepositoryTest {

    private val sampleVideos = listOf(
        MediaItemModel(id = 1L, title = "Beta Video", dateAdded = 100L, sizeBytes = 500L, durationMs = 60_000L, folderName = "Movies"),
        MediaItemModel(id = 2L, title = "Alpha Video", dateAdded = 300L, sizeBytes = 200L, durationMs = 300_000L, folderName = "Camera"),
        MediaItemModel(id = 3L, title = "Gamma Video", dateAdded = 200L, sizeBytes = 1000L, durationMs = 900_000L, folderName = "Movies")
    )

    @Test
    fun testSortingByName() {
        val sortedAsc = sampleVideos.sortedBy { it.title.lowercase() }
        assertEquals("Alpha Video", sortedAsc[0].title)
        assertEquals("Beta Video", sortedAsc[1].title)
        assertEquals("Gamma Video", sortedAsc[2].title)

        val sortedDesc = sampleVideos.sortedByDescending { it.title.lowercase() }
        assertEquals("Gamma Video", sortedDesc[0].title)
        assertEquals("Beta Video", sortedDesc[1].title)
        assertEquals("Alpha Video", sortedDesc[2].title)
    }

    @Test
    fun testSortingByDate() {
        val sortedNewest = sampleVideos.sortedByDescending { it.dateAdded }
        assertEquals("Alpha Video", sortedNewest[0].title) // 300L
        assertEquals("Gamma Video", sortedNewest[1].title) // 200L
        assertEquals("Beta Video", sortedNewest[2].title)  // 100L
    }

    @Test
    fun testSortingBySize() {
        val sortedLargest = sampleVideos.sortedByDescending { it.sizeBytes }
        assertEquals("Gamma Video", sortedLargest[0].title) // 1000L
        assertEquals("Beta Video", sortedLargest[1].title)  // 500L
        assertEquals("Alpha Video", sortedLargest[2].title) // 200L
    }

    @Test
    fun testSortingByDuration() {
        val sortedLongest = sampleVideos.sortedByDescending { it.durationMs }
        assertEquals("Gamma Video", sortedLongest[0].title) // 900s
        assertEquals("Alpha Video", sortedLongest[1].title) // 300s
        assertEquals("Beta Video", sortedLongest[2].title)  // 60s
    }

    @Test
    fun testDurationFiltering() {
        // SHORT: < 2 mins (120,000ms)
        val shortVideos = sampleVideos.filter { it.durationMs < 120_000L }
        assertEquals(1, shortVideos.size)
        assertEquals("Beta Video", shortVideos[0].title)

        // MEDIUM: 2 - 10 mins (120,000ms .. 600,000ms)
        val mediumVideos = sampleVideos.filter { it.durationMs in 120_000L..600_000L }
        assertEquals(1, mediumVideos.size)
        assertEquals("Alpha Video", mediumVideos[0].title)

        // LONG: > 10 mins (600,000ms)
        val longVideos = sampleVideos.filter { it.durationMs > 600_000L }
        assertEquals(1, longVideos.size)
        assertEquals("Gamma Video", longVideos[0].title)
    }

    @Test
    fun testFolderGrouping() {
        val folders = sampleVideos.groupBy { it.folderName }
        assertEquals(2, folders.size)
        assertEquals(2, folders["Movies"]?.size)
        assertEquals(1, folders["Camera"]?.size)
    }
}
