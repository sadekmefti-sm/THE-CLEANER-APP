package com.example.domain.scanner

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

data class VisionSignalMetrics(
    val dHash: Long,
    val laplacianVarianceRaw: Float,
    val sharpnessScore: Float,       // Normalized 0..100
    val exposureScore: Float,        // Normalized 0..100
    val meanLuminance: Float,        // 0..255
    val isUnderExposed: Boolean,
    val isOverExposed: Boolean
)

/**
 * Pure Kotlin 100% On-Device Signal Processing Engine (Zero OpenCV native bloat):
 * 1) 64-bit Perceptual Difference Hash (dHash) over a 9x8 luminance grid.
 * 2) Bitwise XOR Hamming Distance via Long.countOneBits().
 * 3) 3x3 Laplacian Convolution Kernel [0, 1, 0, 1, -4, 1, 0, 1, 0] for sharpness variance.
 * 4) 256-bin Luminance Histogram analysis for dark pocket shots and blown-out highlights.
 */
object PureKotlinVisionEngine {

    /**
     * Computes all pure-Kotlin visual metrics from a downsampled bitmap (e.g. ~256x256).
     */
    fun analyzeBitmap(sourceBitmap: Bitmap): VisionSignalMetrics {
        val dHash = compute64BitDHash(sourceBitmap)

        // Ensure analysis dimension is bounded to at most 256x256 for zero-lag pixel traversal
        val workingBitmap = if (sourceBitmap.width > 256 || sourceBitmap.height > 256) {
            Bitmap.createScaledBitmap(sourceBitmap, 256, 256, true)
        } else {
            sourceBitmap
        }

        val w = workingBitmap.width
        val h = workingBitmap.height
        val totalPixels = max(1, w * h)
        val argbPixels = IntArray(totalPixels)
        workingBitmap.getPixels(argbPixels, 0, w, 0, 0, w, h)

        if (workingBitmap !== sourceBitmap) {
            workingBitmap.recycle()
        }

        // Convert to grayscale 0..255 and build 256-bin luminance histogram simultaneously
        val gray = IntArray(totalPixels)
        val histogram = IntArray(256)
        var luminanceSum = 0L

        for (i in 0 until totalPixels) {
            val px = argbPixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            // ITU-R BT.601 integer luma approximation: Y = (77*R + 150*G + 29*B) >> 8
            val luma = ((77 * r + 150 * g + 29 * b) shr 8).coerceIn(0, 255)
            gray[i] = luma
            histogram[luma]++
            luminanceSum += luma
        }

        val meanLuminance = luminanceSum.toFloat() / totalPixels.toFloat()

        // 3x3 Laplacian Kernel:
        // [ 0,  1,  0 ]
        // [ 1, -4,  1 ]
        // [ 0,  1,  0 ]
        var lapSum = 0.0
        var lapSqSum = 0.0
        var innerCount = 0

        if (w >= 3 && h >= 3) {
            for (y in 1 until (h - 1)) {
                val rowOffset = y * w
                val prevRowOffset = (y - 1) * w
                val nextRowOffset = (y + 1) * w
                for (x in 1 until (w - 1)) {
                    val center = gray[rowOffset + x]
                    val top = gray[prevRowOffset + x]
                    val bottom = gray[nextRowOffset + x]
                    val left = gray[rowOffset + x - 1]
                    val right = gray[rowOffset + x + 1]

                    val response = (top + bottom + left + right) - (4 * center)
                    val responseDouble = response.toDouble()
                    lapSum += responseDouble
                    lapSqSum += responseDouble * responseDouble
                    innerCount++
                }
            }
        }

        val variance = if (innerCount > 1) {
            val mean = lapSum / innerCount
            max(0.0, (lapSqSum / innerCount) - (mean * mean)).toFloat()
        } else {
            0f
        }

        // Normalize Laplacian variance to a human-intuitive 0..100 sharpness score
        // Typical variance on 256x256: < 45 is blurry, 120..400 is sharp, > 600 is ultra-crisp
        val sharpnessScore = if (variance <= 1f) {
            0f
        } else {
            val logMapped = (ln(variance.toDouble() + 1.0) / ln(950.0)) * 100.0
            logMapped.toFloat().coerceIn(0f, 100f)
        }

        // Histogram tail ratios for exposure analysis
        var darkPixels = 0
        var clippedBrightPixels = 0
        for (bin in 0..32) darkPixels += histogram[bin]
        for (bin in 230..255) clippedBrightPixels += histogram[bin]

        val darkRatio = darkPixels.toFloat() / totalPixels.toFloat()
        val brightRatio = clippedBrightPixels.toFloat() / totalPixels.toFloat()

        val isUnderExposed = meanLuminance < 42f || darkRatio > 0.68f
        val isOverExposed = meanLuminance > 222f || brightRatio > 0.65f

        // Exposure score peaks at balanced mid-tones (meanLuminance ~125) and low clipping
        val midtoneDeviation = abs(meanLuminance - 125f) / 125f // 0..1
        val clippingPenalty = max(0f, (darkRatio - 0.35f) * 80f) + max(0f, (brightRatio - 0.35f) * 80f)
        val exposureScore = (100f - (midtoneDeviation * 55f) - clippingPenalty).coerceIn(5f, 100f)

        return VisionSignalMetrics(
            dHash = dHash,
            laplacianVarianceRaw = variance,
            sharpnessScore = sharpnessScore,
            exposureScore = exposureScore,
            meanLuminance = meanLuminance,
            isUnderExposed = isUnderExposed,
            isOverExposed = isOverExposed
        )
    }

    /**
     * Computes a 64-bit horizontal gradient Difference Hash (dHash):
     * 1. Scales bitmap to 9 columns x 8 rows.
     * 2. Compares adjacent horizontal pixels (col < col + 1) across 8 rows x 8 comparisons = 64 bits.
     */
    fun compute64BitDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val pixels = IntArray(9 * 8)
        scaled.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        if (scaled !== bitmap) {
            scaled.recycle()
        }

        var hash = 0L
        var bitIndex = 0
        for (row in 0 until 8) {
            val rowOffset = row * 9
            for (col in 0 until 8) {
                val leftLuma = lumaFromArgb(pixels[rowOffset + col])
                val rightLuma = lumaFromArgb(pixels[rowOffset + col + 1])
                if (leftLuma > rightLuma) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }
        return hash
    }

    /**
     * O(1) CPU instruction Hamming distance between two 64-bit perceptual dHashes.
     */
    fun hammingDistance(hashA: Long, hashB: Long): Int {
        return (hashA xor hashB).countOneBits()
    }

    /**
     * Technical Fidelity Tie-Breaker (0..100):
     * Rewards higher native resolution, higher bytes-per-pixel fidelity, and original DCIM/Camera paths
     * over re-compressed social media or screenshot folders.
     */
    fun computeTechnicalFidelityScore(
        width: Int,
        height: Int,
        sizeBytes: Long,
        relativePath: String,
        mimeType: String
    ): Float {
        val megapixels = (width.toLong() * height.toLong()).toFloat() / 1_000_000f
        // Resolution component (up to 45 pts for 12MP+)
        val resolutionPts = min(45f, (megapixels / 12f) * 45f)

        // File size / bitrate density component (up to 25 pts)
        val sizeMb = sizeBytes.toFloat() / (1024f * 1024f)
        val sizePts = min(25f, (sizeMb / 4.5f) * 25f)

        // Source path & format fidelity (up to 30 pts)
        val pathLower = relativePath.lowercase()
        val pathPts = when {
            pathLower.contains("dcim/camera") -> 25f
            pathLower.contains("dcim") -> 20f
            pathLower.contains("whatsapp") || pathLower.contains("telegram") -> 6f
            pathLower.contains("screenshot") -> 8f
            else -> 15f
        }
        val formatBonus = if (
            mimeType.contains("heic", ignoreCase = true) ||
            mimeType.contains("heif", ignoreCase = true) ||
            mimeType.contains("raw", ignoreCase = true) ||
            mimeType.contains("dng", ignoreCase = true)
        ) 5f else 0f

        return (resolutionPts + sizePts + pathPts + formatBonus).coerceIn(0f, 100f)
    }

    /**
     * Composite Best-Shot Crown Formula:
     * TotalScore = (0.35 * Sharpness) + (0.35 * FaceQuality) + (0.15 * Exposure) + (0.15 * TechnicalFidelity)
     */
    fun computeCompositeBestShotScore(
        sharpnessScore: Float,
        faceQualityScore: Float,
        exposureScore: Float,
        technicalFidelityScore: Float
    ): Float {
        val total = (0.35f * sharpnessScore) +
            (0.35f * faceQualityScore) +
            (0.15f * exposureScore) +
            (0.15f * technicalFidelityScore)
        return total.coerceIn(0f, 100f)
    }

    private fun lumaFromArgb(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (77 * r + 150 * g + 29 * b) shr 8
    }
}
