package com.example.domain.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.local.GenerationRecord
import com.example.data.local.MediaType
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RawMediaStoreRow(
    val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: MediaType,
    val bucketName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val dateAddedSeconds: Long,
    val dateTakenMillis: Long,
    val generationModified: Long
)

/**
 * Queries Android MediaStore for Photos & Videos and compares MediaColumns.GENERATION_MODIFIED
 * against Room records for <50ms incremental delta syncs on subsequent launches.
 */
object MediaStoreIncrementalScanner {

    fun queryMediaStoreDelta(
        contentResolver: ContentResolver,
        existingRecords: Map<Long, GenerationRecord>,
        maxItems: Int
    ): Pair<List<RawMediaStoreRow>, Set<Long>> {
        val currentMediaIds = HashSet<Long>()
        val changedOrNewRows = ArrayList<RawMediaStoreRow>()

        queryCollection(
            contentResolver = contentResolver,
            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.PHOTO,
            existingRecords = existingRecords,
            currentMediaIds = currentMediaIds,
            changedOrNewRows = changedOrNewRows
        )

        queryCollection(
            contentResolver = contentResolver,
            collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.VIDEO,
            existingRecords = existingRecords,
            currentMediaIds = currentMediaIds,
            changedOrNewRows = changedOrNewRows
        )

        // Sort newest first so "Quick Batch (500)" always indexes the user's most recent media first
        changedOrNewRows.sortByDescending { it.dateTakenMillis }
        val boundedRows = if (changedOrNewRows.size > maxItems) {
            changedOrNewRows.take(maxItems)
        } else {
            changedOrNewRows
        }

        // Identify MediaStore IDs deleted outside the app
        val deletedIds = existingRecords.keys.filterTo(HashSet()) { it !in currentMediaIds }
        return boundedRows to deletedIds
    }

    private fun queryCollection(
        contentResolver: ContentResolver,
        collectionUri: Uri,
        mediaType: MediaType,
        existingRecords: Map<Long, GenerationRecord>,
        currentMediaIds: MutableSet<Long>,
        changedOrNewRows: MutableList<RawMediaStoreRow>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.GENERATION_MODIFIED
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        runCatching {
            contentResolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val bucketCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val addedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val takenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val relPathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val durationCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                val genModCol = cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    if (sizeBytes <= 0L) continue

                    currentMediaIds.add(id)
                    val genModified = if (genModCol >= 0) cursor.getLong(genModCol) else 1L

                    val existing = existingRecords[id]
                    if (existing != null && existing.isAnalyzed && existing.generationModified == genModified) {
                        // Unchanged since last scan -> skip expensive decode & ML!
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(collectionUri, id).toString()
                    val displayName = (if (nameCol >= 0) cursor.getString(nameCol) else null) ?: "media_$id"
                    val mimeType = (if (mimeCol >= 0) cursor.getString(mimeCol) else null)
                        ?: if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
                    val bucketName = (if (bucketCol >= 0) cursor.getString(bucketCol) else null) ?: "Camera"
                    val relativePath = (if (relPathCol >= 0) cursor.getString(relPathCol) else null)
                        ?: "DCIM/$bucketName/"
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 1920
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 1080
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val dateAddedSec = if (addedCol >= 0) cursor.getLong(addedCol) else (System.currentTimeMillis() / 1000L)
                    val rawTaken = if (takenCol >= 0) cursor.getLong(takenCol) else 0L
                    val dateTakenMillis = if (rawTaken > 100_000_000_000L) rawTaken else (dateAddedSec * 1000L)

                    changedOrNewRows.add(
                        RawMediaStoreRow(
                            mediaStoreId = id,
                            contentUri = contentUri,
                            displayName = displayName,
                            mimeType = mimeType,
                            mediaType = mediaType,
                            bucketName = bucketName,
                            relativePath = relativePath,
                            sizeBytes = sizeBytes,
                            width = width,
                            height = height,
                            durationMs = durationMs,
                            dateAddedSeconds = dateAddedSec,
                            dateTakenMillis = dateTakenMillis,
                            generationModified = genModified
                        )
                    )
                }
            }
        }
    }

    fun formatMonthYearKey(timestampMillis: Long): String {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        return sdf.format(Date(timestampMillis))
    }
}

/**
 * Helper utility to check PowerManager.isIgnoringBatteryOptimizations() and launch
 * Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to protect long scans against
 * aggressive Samsung/Xiaomi/OnePlus OEM RAM killers.
 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    fun createIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

/**
 * Real MediaStore Sample Seeder for empty emulators or demo testing.
 */
object SampleMediaSeeder {

    fun seedDemoMediaToMediaStore(context: Context): Int {
        val resolver = context.contentResolver
        val baseTime = System.currentTimeMillis()
        var createdCount = 0

        if (insertSyntheticPhoto(resolver, "IMG_BURST_ALPINE_01_SHARP.jpg", "DCIM/Camera", baseTime - 5_000L, 1920, 1440, SeedVariant.BURST_SHARP)) createdCount++
        if (insertSyntheticPhoto(resolver, "IMG_BURST_ALPINE_02_SOFT.jpg", "DCIM/Camera", baseTime - 4_200L, 1920, 1440, SeedVariant.BURST_SOFT)) createdCount++
        if (insertSyntheticPhoto(resolver, "IMG_BURST_ALPINE_03_MOTION.jpg", "DCIM/Camera", baseTime - 3_500L, 1920, 1440, SeedVariant.BURST_SLIGHT_SHIFT)) createdCount++

        if (insertSyntheticPhoto(resolver, "IMG_ARCH_ORIGINAL_4K.jpg", "DCIM/Camera", baseTime - 120_000L, 2560, 1920, SeedVariant.ARCH_CRISP)) createdCount++
        if (insertSyntheticPhoto(resolver, "IMG-WA-ARCH-COPY.jpg", "Pictures/WhatsApp", baseTime - 115_000L, 1280, 960, SeedVariant.ARCH_COMPRESSED)) createdCount++

        if (insertSyntheticPhoto(resolver, "SCAN_TAX_INVOICE_RECEIPT_2026.jpg", "DCIM/Camera", baseTime - 300_000L, 1600, 2200, SeedVariant.OCR_RECEIPT_DOCUMENT)) createdCount++

        if (insertSyntheticPhoto(resolver, "Screenshot_20261115_SocialFeed.jpg", "Pictures/Screenshots", baseTime - 600_000L, 1080, 2400, SeedVariant.SCREENSHOT)) createdCount++

        if (insertSyntheticPhoto(resolver, "IMG_POCKET_DARK_BLUR.jpg", "DCIM/Camera", baseTime - 900_000L, 1600, 1200, SeedVariant.DARK_POCKET_SHOT)) createdCount++

        if (insertSyntheticMp4Video(context, "VID_4K_DEMO_CLIP.mp4", baseTime - 60_000L)) createdCount++

        return createdCount
    }

    private enum class SeedVariant {
        BURST_SHARP,
        BURST_SOFT,
        BURST_SLIGHT_SHIFT,
        ARCH_CRISP,
        ARCH_COMPRESSED,
        OCR_RECEIPT_DOCUMENT,
        SCREENSHOT,
        DARK_POCKET_SHOT
    }

    private fun insertSyntheticPhoto(
        resolver: ContentResolver,
        fileName: String,
        relativeFolder: String,
        dateTakenMs: Long,
        width: Int,
        height: Int,
        variant: SeedVariant
    ): Boolean {
        return runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            when (variant) {
                SeedVariant.BURST_SHARP, SeedVariant.BURST_SOFT, SeedVariant.BURST_SLIGHT_SHIFT -> {
                    paint.shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(Color.rgb(18, 35, 82), Color.rgb(214, 96, 58), Color.rgb(248, 190, 85)),
                        null, Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    paint.shader = null

                    val shiftX = if (variant == SeedVariant.BURST_SLIGHT_SHIFT) 8f else 0f
                    paint.color = Color.rgb(255, 225, 130)
                    canvas.drawCircle(width * 0.65f + shiftX, height * 0.32f, width * 0.11f, paint)

                    if (variant == SeedVariant.BURST_SHARP) {
                        paint.color = Color.rgb(25, 32, 48)
                        paint.strokeWidth = 3f
                        for (i in 0 until width step 14) {
                            val peakY = height * 0.58f + ((i * 19) % 160)
                            canvas.drawLine(i.toFloat(), height.toFloat(), i.toFloat(), peakY, paint)
                        }
                        paint.color = Color.WHITE
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 4f
                        for (r in 20..180 step 12) {
                            canvas.drawCircle(width * 0.65f, height * 0.32f, r.toFloat(), paint)
                        }
                        paint.style = Paint.Style.FILL
                    } else {
                        paint.color = Color.rgb(34, 42, 60)
                        canvas.drawRect(0f, height * 0.62f, width.toFloat(), height.toFloat(), paint)
                    }
                }

                SeedVariant.ARCH_CRISP, SeedVariant.ARCH_COMPRESSED -> {
                    paint.shader = LinearGradient(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(Color.rgb(12, 74, 110), Color.rgb(6, 182, 212)),
                        null, Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    paint.shader = null
                    paint.color = Color.WHITE
                    paint.strokeWidth = if (variant == SeedVariant.ARCH_CRISP) 3f else 8f
                    val stepSize = if (variant == SeedVariant.ARCH_CRISP) 24 else 48
                    for (x in 60 until (width - 60) step stepSize) {
                        canvas.drawLine(x.toFloat(), 80f, x.toFloat(), height - 80f, paint)
                    }
                    for (y in 80 until (height - 80) step stepSize) {
                        canvas.drawLine(60f, y.toFloat(), width - 60f, y.toFloat(), paint)
                    }
                }

                SeedVariant.OCR_RECEIPT_DOCUMENT -> {
                    canvas.drawColor(Color.rgb(246, 245, 240))
                    paint.color = Color.rgb(20, 24, 30)
                    paint.typeface = Typeface.MONOSPACE
                    paint.textSize = 48f
                    val lines = listOf(
                        "CLEANVAULT HARDWARE STUDIO INC.",
                        "TAX INVOICE & OFFICIAL RECEIPT",
                        "Invoice #: INV-2026-994812",
                        "Date: 2026-11-15  14:22:09",
                        "Merchant ID: PASSPORT-ID-88412",
                        "--------------------------------",
                        "1x NVMe Pro Storage 2TB   $189.00",
                        "1x Optical Lens Filter     $54.50",
                        "1x Warranty Serial #SN994  $29.00",
                        "--------------------------------",
                        "Subtotal:                 $272.50",
                        "VAT / Sales Tax (8%):      $21.80",
                        "TOTAL AMOUNT PAID:        $294.30",
                        "Payment Method: VISA **** 4821",
                        "Status: APPROVED - KEEP FOR TAX"
                    )
                    var yPos = 160f
                    for (line in lines) {
                        canvas.drawText(line, 90f, yPos, paint)
                        yPos += 115f
                    }
                }

                SeedVariant.SCREENSHOT -> {
                    canvas.drawColor(Color.rgb(18, 22, 31))
                    paint.color = Color.rgb(45, 55, 72)
                    for (card in 0..4) {
                        val top = 160f + card * 410f
                        canvas.drawRoundRect(RectF(60f, top, width - 60f, top + 340f), 32f, 32f, paint)
                    }
                }

                SeedVariant.DARK_POCKET_SHOT -> {
                    canvas.drawColor(Color.rgb(12, 11, 14))
                    paint.color = Color.rgb(22, 20, 25)
                    canvas.drawCircle(width * 0.4f, height * 0.5f, width * 0.25f, paint)
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, dateTakenMs)
                put(MediaStore.Images.Media.DATE_ADDED, dateTakenMs / 1000L)
                put(MediaStore.Images.Media.WIDTH, width)
                put(MediaStore.Images.Media.HEIGHT, height)
                put(MediaStore.Images.Media.RELATIVE_PATH, relativeFolder)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val quality = if (variant == SeedVariant.ARCH_COMPRESSED) 65 else 96
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            bitmap.recycle()

            val complete = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, complete, null, null)
            true
        }.getOrDefault(false)
    }

    private fun insertSyntheticMp4Video(
        context: Context,
        fileName: String,
        dateTakenMs: Long
    ): Boolean {
        return runCatching {
            val tempFile = File(context.cacheDir, "seed_temp_${System.currentTimeMillis()}.mp4")
            val width = 1280
            val height = 720
            val bitRate = 6_000_000
            val frameRate = 24
            val totalFrames = 72

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var muxerStarted = false

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            for (frameIdx in 0 until totalFrames) {
                val canvas = inputSurface.lockCanvas(null)
                val progress = frameIdx.toFloat() / totalFrames.toFloat()
                canvas.drawColor(Color.rgb(15, 23, 42))
                paint.color = Color.rgb(6, 182, 212)
                val cx = width * (0.2f + 0.6f * progress)
                val cy = height * 0.5f
                canvas.drawCircle(cx, cy, 140f, paint)
                paint.color = Color.rgb(245, 158, 11)
                canvas.drawCircle(width - cx, cy, 95f, paint)
                inputSurface.unlockCanvasAndPost(canvas)

                drainEncoder(encoder, muxer, bufferInfo, false, trackIndex, muxerStarted).also {
                    trackIndex = it.first
                    muxerStarted = it.second
                }
            }

            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufferInfo, true, trackIndex, muxerStarted)

            encoder.stop()
            encoder.release()
            inputSurface.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, dateTakenMs)
                put(MediaStore.Video.Media.DATE_ADDED, dateTakenMs / 1000L)
                put(MediaStore.Video.Media.WIDTH, width)
                put(MediaStore.Video.Media.HEIGHT, height)
                put(MediaStore.Video.Media.DURATION, 3000L)
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CleanVaultDemo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { input -> input.copyTo(out) }
            }
            tempFile.delete()

            val complete = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, complete, null, null)
            true
        }.getOrDefault(false)
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        currentTrackIndex: Int,
        currentMuxerStarted: Boolean
    ): Pair<Int, Boolean> {
        var trackIndex = currentTrackIndex
        var muxerStarted = currentMuxerStarted
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
            } else if (status >= 0) {
                val encodedData: ByteBuffer = encoder.getOutputBuffer(status) ?: continue
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
                encoder.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
        return trackIndex to muxerStarted
    }
}
