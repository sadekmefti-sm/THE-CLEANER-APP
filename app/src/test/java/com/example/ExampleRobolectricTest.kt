package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.domain.scanner.PureKotlinVisionEngine
import org.junit.Assert.assertEquals
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
}
