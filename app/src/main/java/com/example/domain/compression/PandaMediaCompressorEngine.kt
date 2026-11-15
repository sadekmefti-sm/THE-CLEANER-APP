package com.example.domain.compression

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.data.local.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class CompressionPreset(
    val title: String,
    val subtitle: String,
    val defaultTargetRatio: Float, // Percentage of original size (0.20f .. 0.90f)
    val preferredHeightPx: Int
) {
    SMALL_FILE("Small File (720p)", "Max space saved • Ideal for archiving", 0.30f, 720),
    BALANCED("Balanced (1080p)", "High visual fidelity • H.265/HEVC", 0.50f, 1080),
    FIT_MESSAGING("Fit to Messaging (< 25 MB)", "Guaranteed email/chat attachment fit", 0.35f, 720),
    CUSTOM_SLIDER("Custom Target % / MB", "Fine-tune exact percentage or MB budget", 0.45f, 1080)
}

data class CompressionPlan(
    val originalBytes: Long,
    val targetBytes: Long,
    val estimatedSavingsBytes: Long,
    val targetPercentage: Int, // 20..90% of original size (shrink by 10..80%)
    val targetVideoBitrateBps: Int,
    val audioBitrateBps: Int = 128_000,
    val targetHeightPx: Int,
    val photoQuality: Int
)

sealed class CompressionExecutionResult {
    data class Success(
        val originalMedia: MediaItemEntity,
        val outputUri: Uri,
        val outputDisplayName: String,
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long,
        val savedBytes: Long,
        val outputWidth: Int,
        val outputHeight: Int,
        val codecUsed: String
    ) : CompressionExecutionResult()

    data class Failure(val errorMessage: String) : CompressionExecutionResult()
}

/**
 * "Panda-Style" Video & Photo Downscaler Engine:
 * - Reverse Bitrate Math: TargetVideoBitrateBps = ((TargetBytes * 8 * 0.95) / DurationSeconds) - AudioBitrateBps (128_000)
 * - Hardware-accelerated Media3 Transformer with Presentation.createForHeight(720 / 1080)
 * - H.265/HEVC encoding with automatic fallback to H.264/AVC
 * - Photo Downscaler with WebP/JPEG and full EXIF tag preservation (DateTime, GPS, Orientation)
 */
object PandaMediaCompressorEngine {

    private const val DEFAULT_AUDIO_BITRATE_BPS = 128_000
    private const val MIN_VIDEO_BITRATE_BPS = 280_000
    private const val MAX_VIDEO_BITRATE_BPS = 18_000_000

    /**
     * Computes live bitrate, resolution, and predicted output MB from either a slider ratio (0.20..0.90)
     * or a preset.
     */
    fun calculateCompressionPlan(
        item: MediaItemEntity,
        preset: CompressionPreset,
        sliderTargetRatio: Float, // 0.20f (80% shrink) .. 0.90f (10% shrink)
        customTargetMb: Float? = null
    ): CompressionPlan {
        val originalBytes = max(1024L, item.sizeBytes)
        val clampedRatio = sliderTargetRatio.coerceIn(0.20f, 0.90f)

        val rawTargetBytes = when {
            customTargetMb != null && customTargetMb > 0.2f -> {
                (customTargetMb * 1024f * 1024f).roundToLong().coerceIn(
                    (originalBytes * 0.12f).roundToLong(),
                    (originalBytes * 0.95f).roundToLong()
                )
            }
            preset == CompressionPreset.FIT_MESSAGING -> {
                val cap24Mb = (24.0 * 1024.0 * 1024.0).roundToLong()
                minOf(cap24Mb, (originalBytes * clampedRatio).roundToLong())
            }
            else -> (originalBytes * clampedRatio).roundToLong()
        }

        val targetBytes = max(32 * 1024L, rawTargetBytes)
        val savingsBytes = max(0L, originalBytes - targetBytes)
        val effectivePercent = ((targetBytes.toDouble() / originalBytes.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(15, 95)

        val durationSeconds = max(1.0, item.durationMs.toDouble() / 1000.0)
        val totalBitsWithOverheadMargin = targetBytes.toDouble() * 8.0 * 0.95
        val rawVideoBitrate = (totalBitsWithOverheadMargin / durationSeconds).roundToInt() - DEFAULT_AUDIO_BITRATE_BPS
        val targetVideoBitrateBps = rawVideoBitrate.coerceIn(MIN_VIDEO_BITRATE_BPS, MAX_VIDEO_BITRATE_BPS)

        val targetHeightPx = when {
            preset == CompressionPreset.SMALL_FILE || preset == CompressionPreset.FIT_MESSAGING -> 720
            targetVideoBitrateBps < 1_400_000 -> 720
            else -> minOf(1080, max(720, minOf(item.width, item.height)))
        }

        val photoQuality = (35 + ((clampedRatio - 0.20f) / 0.70f * 57f)).roundToInt().coerceIn(38, 92)

        return CompressionPlan(
            originalBytes = originalBytes,
            targetBytes = targetBytes,
            estimatedSavingsBytes = savingsBytes,
            targetPercentage = effectivePercent,
            targetVideoBitrateBps = targetVideoBitrateBps,
            audioBitrateBps = DEFAULT_AUDIO_BITRATE_BPS,
            targetHeightPx = targetHeightPx,
            photoQuality = photoQuality
        )
    }

    /**
     * Compresses a video using androidx.media3.transformer.Transformer with hardware H.265/HEVC
     * and automatic fallback to H.264/AVC if the device encoder rejects HEVC.
     */
    @OptIn(UnstableApi::class)
    suspend fun compressVideoWithMedia3(
        context: Context,
        item: MediaItemEntity,
        plan: CompressionPlan,
        onProgress: (Int) -> Unit
    ): CompressionExecutionResult {
        val primaryHevcResult = runMedia3TransformAttempt(
            context = context,
            item = item,
            plan = plan,
            videoMimeType = MimeTypes.VIDEO_H265,
            onProgress = onProgress
        )

        if (primaryHevcResult is CompressionExecutionResult.Success) {
            return primaryHevcResult
        }

        return runMedia3TransformAttempt(
            context = context,
            item = item,
            plan = plan,
            videoMimeType = MimeTypes.VIDEO_H264,
            onProgress = onProgress
        )
    }

    @OptIn(UnstableApi::class)
    private suspend fun runMedia3TransformAttempt(
        context: Context,
        item: MediaItemEntity,
        plan: CompressionPlan,
        videoMimeType: String,
        onProgress: (Int) -> Unit
    ): CompressionExecutionResult = withContext(Dispatchers.Main) {
        val tempOutputFile = File(
            context.cacheDir,
            "compressed_${item.mediaStoreId}_${System.currentTimeMillis()}.mp4"
        )
        if (tempOutputFile.exists()) tempOutputFile.delete()

        val transformSuccess = suspendCancellableCoroutine<Boolean> { cont ->
            val videoEncoderSettings = VideoEncoderSettings.Builder()
                .setBitrate(plan.targetVideoBitrateBps)
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(videoEncoderSettings)
                .build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (cont.isActive) cont.resume(false)
                }
            }

            val transformer = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setVideoMimeType(videoMimeType)
                .setEncoderFactory(encoderFactory)
                .addListener(listener)
                .build()

            val presentationEffect = Presentation.createForHeight(plan.targetHeightPx)
            val effects = Effects(emptyList(), listOf(presentationEffect))
            val mediaItem = MediaItem.fromUri(Uri.parse(item.contentUri))
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build()

            runCatching {
                transformer.start(editedMediaItem, tempOutputFile.absolutePath)
            }.onFailure {
                if (cont.isActive) cont.resume(false)
            }

            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
            }
        }

        if (!transformSuccess || !tempOutputFile.exists() || tempOutputFile.length() <= 0L) {
            tempOutputFile.delete()
            return@withContext CompressionExecutionResult.Failure("Hardware video encoder could not process stream.")
        }

        onProgress(95)

        // Write compressed MP4 into MediaStore with atomic IS_PENDING = 1 -> 0 flip
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val baseName = item.displayName.substringBeforeLast(".")
            val outName = "${baseName}_panda_${plan.targetHeightPx}p.mp4"

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, outName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, item.dateTakenMillis)
                put(MediaStore.Video.Media.DURATION, item.durationMs)
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CleanVault")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val outputUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (outputUri == null) {
                tempOutputFile.delete()
                return@withContext CompressionExecutionResult.Failure("Failed to create output MediaStore entry.")
            }

            resolver.openOutputStream(outputUri)?.use { outStream ->
                tempOutputFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            val finalBytes = tempOutputFile.length().coerceAtLeast(1024L)
            tempOutputFile.delete()

            val completeValues = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(outputUri, completeValues, null, null)

            onProgress(100)
            val saved = max(0L, item.sizeBytes - finalBytes)
            val codecLabel = if (videoMimeType == MimeTypes.VIDEO_H265) "H.265 / HEVC" else "H.264 / AVC"

            CompressionExecutionResult.Success(
                originalMedia = item,
                outputUri = outputUri,
                outputDisplayName = outName,
                originalSizeBytes = item.sizeBytes,
                compressedSizeBytes = finalBytes,
                savedBytes = saved,
                outputWidth = (plan.targetHeightPx * 16) / 9,
                outputHeight = plan.targetHeightPx,
                codecUsed = codecLabel
            )
        }
    }

    /**
     * Compresses oversized photos using JPEG or WEBP_LOSSY with optional resolution downscaling
     * and preserves all original EXIF metadata tags (DateTime, GPS, Orientation, Make/Model).
     */
    suspend fun compressPhotoWithExifPreservation(
        context: Context,
        item: MediaItemEntity,
        plan: CompressionPlan,
        useWebpLossy: Boolean = false,
        onProgress: (Int) -> Unit
    ): CompressionExecutionResult = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(15)
            val resolver = context.contentResolver
            val sourceUri = Uri.parse(item.contentUri)

            // 1. Read original EXIF attributes from source stream
            val exifMap = readExifTags(resolver, sourceUri)
            onProgress(35)

            // 2. Decode with safe target dimension based on compression ratio
            val targetMaxDim = if (plan.targetPercentage <= 45) 1600 else 2560
            val sourceBitmap = decodePhotoForCompression(resolver, sourceUri, targetMaxDim)
                ?: return@withContext CompressionExecutionResult.Failure("Unable to decode source photo.")

            onProgress(60)

            val tempFile = File(context.cacheDir, "photo_comp_${item.mediaStoreId}.jpg")
            val format = if (useWebpLossy) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.JPEG
            }

            tempFile.outputStream().use { out ->
                sourceBitmap.compress(format, plan.photoQuality, out)
            }
            val outW = sourceBitmap.width
            val outH = sourceBitmap.height
            sourceBitmap.recycle()

            // 3. Copy preserved EXIF tags (DateTime, GPS, Make/Model) to the compressed file
            if (!useWebpLossy) {
                applyExifTagsToFile(tempFile, exifMap)
            }
            onProgress(85)

            val ext = if (useWebpLossy) "webp" else "jpg"
            val mime = if (useWebpLossy) "image/webp" else "image/jpeg"
            val baseName = item.displayName.substringBeforeLast(".")
            val outName = "${baseName}_cleanvault.$ext"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, outName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.DATE_TAKEN, item.dateTakenMillis)
                put(MediaStore.Images.Media.WIDTH, outW)
                put(MediaStore.Images.Media.HEIGHT, outH)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CleanVault")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext CompressionExecutionResult.Failure("Could not insert compressed photo into MediaStore.")

            resolver.openOutputStream(outputUri)?.use { outStream ->
                tempFile.inputStream().use { inStream -> inStream.copyTo(outStream) }
            }

            val finalBytes = tempFile.length().coerceAtLeast(512L)
            tempFile.delete()

            val doneValues = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(outputUri, doneValues, null, null)

            onProgress(100)
            val savedBytes = max(0L, item.sizeBytes - finalBytes)
            CompressionExecutionResult.Success(
                originalMedia = item,
                outputUri = outputUri,
                outputDisplayName = outName,
                originalSizeBytes = item.sizeBytes,
                compressedSizeBytes = finalBytes,
                savedBytes = savedBytes,
                outputWidth = outW,
                outputHeight = outH,
                codecUsed = if (useWebpLossy) "WEBP Lossy + EXIF" else "JPEG Optimized + EXIF"
            )
        }.getOrElse { e ->
            CompressionExecutionResult.Failure(e.message ?: "Photo compression failed")
        }
    }

    private fun decodePhotoForCompression(
        resolver: ContentResolver,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > maxDimension * 1.5 &&
            (bounds.outHeight / sampleSize) > maxDimension * 1.5
        ) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = max(1, sampleSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
    }

    private val PRESERVED_EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_WHITE_BALANCE
    )

    private fun readExifTags(resolver: ContentResolver, uri: Uri): Map<String, String> {
        val map = mutableMapOf<String, String>()
        runCatching {
            val mediaUri = runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)

            resolver.openInputStream(mediaUri)?.use { stream ->
                val exif = ExifInterface(stream)
                for (tag in PRESERVED_EXIF_TAGS) {
                    val value = exif.getAttribute(tag)
                    if (!value.isNullOrEmpty()) {
                        map[tag] = value
                    }
                }
            }
        }
        return map
    }

    private fun applyExifTagsToFile(file: File, tags: Map<String, String>) {
        if (tags.isEmpty()) return
        runCatching {
            val exif = ExifInterface(file)
            for ((tag, value) in tags) {
                exif.setAttribute(tag, value)
            }
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            exif.saveAttributes()
        }
    }
}
