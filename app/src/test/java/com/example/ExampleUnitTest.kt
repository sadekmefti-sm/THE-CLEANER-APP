package com.example

import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.domain.compression.CompressionPreset
import com.example.domain.compression.PandaMediaCompressorEngine
import com.example.domain.scanner.PureKotlinVisionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun hammingDistance_computesExactBitDifference() {
        val hashA = 0b1010_1111_0000L
        val hashB = 0b1010_0000_0000L
        val dist = PureKotlinVisionEngine.hammingDistance(hashA, hashB)
        assertEquals(4, dist)
    }

    @Test
    fun compositeBestShotScore_weightsComponentsCorrectly() {
        val score = PureKotlinVisionEngine.computeCompositeBestShotScore(
            sharpnessScore = 80f,         // 0.35 * 80 = 28.0
            faceQualityScore = 90f,       // 0.35 * 90 = 31.5
            exposureScore = 70f,          // 0.15 * 70 = 10.5
            technicalFidelityScore = 60f  // 0.15 * 60 = 9.0
        )
        assertEquals(79.0f, score, 0.01f)
    }

    @Test
    fun pandaReverseBitrateMath_calculatesTargetBytesAndBitrate() {
        val sampleVideo = MediaItemEntity(
            mediaStoreId = 101L,
            contentUri = "content://media/external/video/media/101",
            displayName = "4K_Sample.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            sizeBytes = 100L * 1024L * 1024L, // 100 MB
            width = 3840,
            height = 2160,
            durationMs = 60_000L, // 60 seconds
            dateAddedSeconds = 1_700_000_000L,
            dateTakenMillis = 1_700_000_000_000L,
            generationModified = 10L
        )

        val plan = PandaMediaCompressorEngine.calculateCompressionPlan(
            item = sampleVideo,
            preset = CompressionPreset.CUSTOM_SLIDER,
            sliderTargetRatio = 0.30f // Shrink by 70% -> 30 MB target
        )

        assertEquals(30, plan.targetPercentage)
        assertTrue(plan.targetBytes < sampleVideo.sizeBytes)
        assertTrue(plan.targetVideoBitrateBps in 280_000..18_000_000)
    }
}
