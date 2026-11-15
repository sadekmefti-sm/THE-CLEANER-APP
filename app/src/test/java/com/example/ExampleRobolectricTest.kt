package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.domain.billing.LocalEntitlementManager
import com.example.domain.billing.PremiumFeature
import com.example.domain.scanner.PureKotlinVisionEngine
import com.example.domain.storage.ScopedStorageTrashManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CleanVault", appName)
    }

    @Test
    fun `laplacian sharpness distinguishes crisp checkerboard from flat blur`() {
        val size = 64
        val flatPixels = IntArray(size * size) { Color.rgb(120, 120, 120) }
        val flatBitmap = Bitmap.createBitmap(flatPixels, size, size, Bitmap.Config.ARGB_8888)

        val crispPixels = IntArray(size * size) { idx ->
            val x = idx % size
            val y = idx / size
            if ((x + y) % 2 == 0) Color.WHITE else Color.BLACK
        }
        val crispBitmap = Bitmap.createBitmap(crispPixels, size, size, Bitmap.Config.ARGB_8888)

        val flatMetrics = PureKotlinVisionEngine.analyzeBitmap(flatBitmap)
        val crispMetrics = PureKotlinVisionEngine.analyzeBitmap(crispBitmap)

        assertTrue(crispMetrics.sharpnessScore > flatMetrics.sharpnessScore)
    }

    @Test
    fun `isFeatureAllowed returns immediately using DataStore first()`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = LocalEntitlementManager(context)

        assertTrue(manager.isFeatureAllowed(PremiumFeature.ONE_TAP_SMART_CLEAN))

        manager.setProUnlockedForTesting(false)
        assertFalse(manager.isFeatureAllowed(PremiumFeature.UNLIMITED_BATCH_COMPRESS))

        manager.setProUnlockedForTesting(true)
        assertTrue(manager.isFeatureAllowed(PremiumFeature.UNLIMITED_BATCH_COMPRESS))
    }

    @Test
    fun `chunkStagedItemsIntoBatches splits over 1000 items into bounded batches`() {
        val items = (1L..2350L).map { id ->
            MediaItemEntity(
                mediaStoreId = id,
                contentUri = "content://media/external/images/media/$id",
                displayName = "IMG_$id.jpg",
                mimeType = "image/jpeg",
                mediaType = MediaType.PHOTO,
                bucketName = "Camera",
                relativePath = "DCIM/Camera/",
                sizeBytes = 1000L,
                width = 1920,
                height = 1080,
                durationMs = 0L,
                dateAddedSeconds = 1_700_000_000L,
                dateTakenMillis = 1_700_000_000_000L,
                generationModified = 1L
            )
        }

        val batches = ScopedStorageTrashManager.chunkStagedItemsIntoBatches(items)
        assertEquals(3, batches.size)
        assertEquals(1000, batches[0].mediaIds.size)
        assertEquals(1000, batches[1].mediaIds.size)
        assertEquals(350, batches[2].mediaIds.size)
        assertEquals(1L, batches[0].mediaIds.first())
        assertEquals(1000L, batches[0].mediaIds.last())
        assertEquals(2350_000L, batches.sumOf { it.totalBytes })
    }
}
