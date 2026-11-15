package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.example.data.local.CleanVaultDatabase
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.data.local.ScanMode
import com.example.data.local.ScanTelemetryEntity
import com.example.data.local.SensitivityLevel
import com.example.data.local.TriageStatus
import com.example.domain.clustering.SimilarityClusterEngine
import com.example.domain.ml.MlKitQualityAndOcrAnalyzer
import com.example.domain.scanner.MediaStoreIncrementalScanner
import com.example.domain.scanner.PureKotlinVisionEngine
import com.example.domain.scanner.RawMediaStoreRow
import com.example.domain.scanner.SafeBitmapDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object ScanControlBus {
    val isPaused = AtomicBoolean(false)
    val isCancelled = AtomicBoolean(false)
}

class ScanNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val db = CleanVaultDatabase.getInstance(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        when (intent?.action) {
            GalleryScanWorker.ACTION_PAUSE_SCAN -> {
                ScanControlBus.isPaused.set(true)
                scope.launch {
                    val current = db.mediaDao().getScanTelemetry() ?: ScanTelemetryEntity()
                    db.mediaDao().upsertScanTelemetry(
                        current.copy(isPaused = true, statusMessage = "Scan paused by user")
                    )
                }
            }
            GalleryScanWorker.ACTION_RESUME_SCAN -> {
                ScanControlBus.isPaused.set(false)
                scope.launch {
                    val current = db.mediaDao().getScanTelemetry() ?: ScanTelemetryEntity()
                    db.mediaDao().upsertScanTelemetry(
                        current.copy(isPaused = false, statusMessage = "Resuming gallery scan...")
                    )
                }
            }
            GalleryScanWorker.ACTION_CANCEL_SCAN -> {
                ScanControlBus.isCancelled.set(true)
                ScanControlBus.isPaused.set(false)
                WorkManager.getInstance(context).cancelUniqueWork(GalleryScanWorker.UNIQUE_WORK_NAME)
                scope.launch {
                    val current = db.mediaDao().getScanTelemetry() ?: ScanTelemetryEntity()
                    db.mediaDao().upsertScanTelemetry(
                        current.copy(
                            isScanning = false,
                            isPaused = false,
                            statusMessage = "Scan cancelled"
                        )
                    )
                }
            }
        }
    }
}

/**
 * Android 14/15+ Compliant Foreground Media Processing Scanner Worker:
 * - Promoted via setForeground() with ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
 * - Bounded coroutine dispatcher: Dispatchers.Default.limitedParallelism(2)
 * - Chunked Room @Transaction upserts in batches of 100 items
 * - Live NotificationCompat progress with Pause/Resume and Cancel PendingIntents
 */
class GalleryScanWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "CleanVaultGalleryScanWork"
        const val KEY_SCAN_MODE = "scan_mode"
        const val KEY_HAMMING_BITS = "hamming_bits"
        const val KEY_AUTO_QUEUE_REMAINDER = "auto_queue_remainder"

        const val NOTIFICATION_ID = 4102
        const val CHANNEL_ID = "cleanvault_media_scan_channel"

        const val ACTION_PAUSE_SCAN = "com.example.action.PAUSE_SCAN"
        const val ACTION_RESUME_SCAN = "com.example.action.RESUME_SCAN"
        const val ACTION_CANCEL_SCAN = "com.example.action.CANCEL_SCAN"

        fun enqueueScan(
            context: Context,
            scanMode: ScanMode,
            sensitivityLevel: SensitivityLevel,
            autoQueueRemainder: Boolean = (scanMode == ScanMode.QUICK_BATCH)
        ) {
            ScanControlBus.isCancelled.set(false)
            ScanControlBus.isPaused.set(false)

            val request = OneTimeWorkRequestBuilder<GalleryScanWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SCAN_MODE to scanMode.name,
                        KEY_HAMMING_BITS to sensitivityLevel.maxHammingDistance,
                        KEY_AUTO_QUEUE_REMAINDER to autoQueueRemainder
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private val boundedAnalysisDispatcher = Dispatchers.Default.limitedParallelism(2)
    private val mlAnalyzer = MlKitQualityAndOcrAnalyzer()

    override suspend fun doWork(): Result {
        val db = CleanVaultDatabase.getInstance(appContext)
        val dao = db.mediaDao()
        val contentResolver = appContext.contentResolver

        val modeName = inputData.getString(KEY_SCAN_MODE) ?: ScanMode.QUICK_BATCH.name
        val scanMode = runCatching { ScanMode.valueOf(modeName) }.getOrDefault(ScanMode.QUICK_BATCH)
        val hammingBits = inputData.getInt(KEY_HAMMING_BITS, SensitivityLevel.BALANCED.maxHammingDistance)
        val sensitivity = SensitivityLevel.entries.firstOrNull { it.maxHammingDistance == hammingBits }
            ?: SensitivityLevel.BALANCED
        val autoQueueRemainder = inputData.getBoolean(KEY_AUTO_QUEUE_REMAINDER, false)

        ensureNotificationChannel()
        promoteToForegroundSafely(0, 100, 0L, isPaused = false)

        val existingTelemetry = dao.getScanTelemetry() ?: ScanTelemetryEntity()
        dao.upsertScanTelemetry(
            existingTelemetry.copy(
                isScanning = true,
                isPaused = false,
                scanMode = scanMode,
                statusMessage = "Checking MediaStore generation delta..."
            )
        )

        // 1. Incremental Delta Query (<50ms on subsequent launches)
        val generationList = dao.getAllGenerationRecords()
        val generationMap = generationList.associateBy { it.mediaStoreId }

        val (deltaRows, deletedIds) = MediaStoreIncrementalScanner.queryMediaStoreDelta(
            contentResolver = contentResolver,
            existingRecords = generationMap,
            maxItems = scanMode.maxInitialItems
        )

        if (deletedIds.isNotEmpty()) {
            dao.deleteMediaByIds(deletedIds.toList())
        }

        val totalToProcess = deltaRows.size
        var processedCount = 0
        var clutterBytesFound = 0L

        // 2. Process in chunked batches of 100 items using bounded parallelism (2 threads max)
        val chunks = deltaRows.chunked(100)
        for (chunk in chunks) {
            if (isStopped || ScanControlBus.isCancelled.get()) {
                dao.upsertScanTelemetry(
                    (dao.getScanTelemetry() ?: ScanTelemetryEntity()).copy(
                        isScanning = false,
                        isPaused = false,
                        statusMessage = "Scan stopped"
                    )
                )
                return Result.failure()
            }

            // Respect Pause action from notification or UI
            while (ScanControlBus.isPaused.get() && !isStopped && !ScanControlBus.isCancelled.get()) {
                promoteToForegroundSafely(processedCount, totalToProcess, clutterBytesFound, isPaused = true)
                delay(400L)
            }

            val analyzedBatch = coroutineScope {
                chunk.map { rawRow ->
                    async(boundedAnalysisDispatcher) {
                        val existingTriage = generationMap[rawRow.mediaStoreId]?.triageStatus
                            ?: TriageStatus.UNREVIEWED
                        analyzeSingleMediaRow(rawRow, existingTriage)
                    }
                }.awaitAll()
            }

            // Chunked @Transaction insert (100 items max per transaction)
            dao.upsertMediaChunked(analyzedBatch, 100)

            processedCount += analyzedBatch.size
            clutterBytesFound += analyzedBatch.filter {
                it.isBlurryOrBad || it.isScreenshot || it.isSocialMediaClutter ||
                    (it.mediaType == MediaType.VIDEO && it.sizeBytes > 15 * 1024 * 1024L)
            }.sumOf { it.sizeBytes }

            val percent = if (totalToProcess > 0) (processedCount * 100) / totalToProcess else 100
            val statusText = "Scanning Gallery: $processedCount / $totalToProcess ($percent%) • ${formatBytes(clutterBytesFound)} clutter"

            dao.upsertScanTelemetry(
                (dao.getScanTelemetry() ?: ScanTelemetryEntity()).copy(
                    isScanning = true,
                    isPaused = false,
                    scanMode = scanMode,
                    scannedCount = processedCount,
                    totalLibraryCount = maxOf(totalToProcess, dao.getTotalCount()),
                    clutterBytesFound = clutterBytesFound,
                    statusMessage = statusText
                )
            )
            promoteToForegroundSafely(processedCount, totalToProcess, clutterBytesFound, isPaused = false)
        }

        // 3. Run Sub-Quadratic O(N log N) Similarity & Burst Clustering
        dao.upsertScanTelemetry(
            (dao.getScanTelemetry() ?: ScanTelemetryEntity()).copy(
                isScanning = true,
                statusMessage = "Clustering burst shots & crowning Best Shots..."
            )
        )

        val allAnalyzedPhotos = dao.getAnalyzedPhotosForClustering()
        val clusteringOutput = SimilarityClusterEngine.buildClusters(
            contentResolver = contentResolver,
            analyzedPhotos = allAnalyzedPhotos,
            sensitivity = sensitivity
        )
        dao.replaceAllClusters(
            clusters = clusteringOutput.clusters,
            updatedItems = clusteringOutput.updatedMediaItems
        )

        val clusterReclaimable = clusteringOutput.clusters.sumOf { it.reclaimableBytes }
        val finalClutterBytes = maxOf(clutterBytesFound, clusterReclaimable)
        val totalIndexed = dao.getTotalCount()

        dao.upsertScanTelemetry(
            (dao.getScanTelemetry() ?: ScanTelemetryEntity()).copy(
                isScanning = false,
                isPaused = false,
                scannedCount = totalIndexed,
                totalLibraryCount = totalIndexed,
                clutterBytesFound = finalClutterBytes,
                lastScanTimestampMillis = System.currentTimeMillis(),
                statusMessage = "Scan complete • ${clusteringOutput.clusters.size} clusters crowned"
            )
        )

        // If Quick Batch (500 items) was chosen and library has more unindexed items, queue remainder
        if (autoQueueRemainder && deltaRows.size >= ScanMode.QUICK_BATCH.maxInitialItems) {
            enqueueScan(
                context = appContext,
                scanMode = ScanMode.FULL_DEEP_SCAN,
                sensitivityLevel = sensitivity,
                autoQueueRemainder = false
            )
        }

        return Result.success()
    }

    private suspend fun analyzeSingleMediaRow(
        row: RawMediaStoreRow,
        preservedTriageStatus: TriageStatus
    ): MediaItemEntity {
        val resolver = appContext.contentResolver
        val uri = Uri.parse(row.contentUri)
        val partialHash = SafeBitmapDecoder.computePartial64KbHash(resolver, uri, row.sizeBytes)
        val monthYear = MediaStoreIncrementalScanner.formatMonthYearKey(row.dateTakenMillis)

        val lowerName = row.displayName.lowercase(Locale.US)
        val lowerPath = row.relativePath.lowercase(Locale.US)
        val lowerBucket = row.bucketName.lowercase(Locale.US)

        val isScreenshot = lowerName.contains("screenshot") ||
            lowerPath.contains("screenshot") ||
            lowerBucket.contains("screenshot")

        val isSocialMedia = lowerPath.contains("whatsapp") ||
            lowerPath.contains("telegram") ||
            lowerPath.contains("instagram") ||
            lowerName.startsWith("img-wa") ||
            lowerName.startsWith("vid-wa")

        val techFidelity = PureKotlinVisionEngine.computeTechnicalFidelityScore(
            width = row.width,
            height = row.height,
            sizeBytes = row.sizeBytes,
            relativePath = row.relativePath,
            mimeType = row.mimeType
        )

        if (row.mediaType == MediaType.VIDEO) {
            return MediaItemEntity(
                mediaStoreId = row.mediaStoreId,
                contentUri = row.contentUri,
                displayName = row.displayName,
                mimeType = row.mimeType,
                mediaType = MediaType.VIDEO,
                bucketName = row.bucketName,
                relativePath = row.relativePath,
                sizeBytes = row.sizeBytes,
                width = row.width,
                height = row.height,
                durationMs = row.durationMs,
                dateAddedSeconds = row.dateAddedSeconds,
                dateTakenMillis = row.dateTakenMillis,
                generationModified = row.generationModified,
                partialSha256 = partialHash,
                isAnalyzed = true,
                technicalFidelityScore = techFidelity,
                compositeBestShotScore = techFidelity,
                isScreenshot = false,
                isSocialMediaClutter = isSocialMedia,
                triageStatus = preservedTriageStatus,
                monthYearKey = monthYear
            )
        }

        // Photo Pipeline:
        // 1. Decode 256x256 bitmap for pure-Kotlin dHash, 3x3 Laplacian variance, and luminance histogram
        val bitmap256 = SafeBitmapDecoder.decodeSubsampledBitmap(resolver, uri, 256)
        val visionMetrics = if (bitmap256 != null) {
            val metrics = PureKotlinVisionEngine.analyzeBitmap(bitmap256)
            bitmap256.recycle()
            metrics
        } else {
            null
        }

        // 2. Decode 512x512 bitmap for ML Kit Face Detection & ML Kit Text Recognition (OCR Guardrail)
        val bitmap512 = SafeBitmapDecoder.decodeSubsampledBitmap(resolver, uri, 512)
        val faceResult = if (bitmap512 != null) {
            mlAnalyzer.analyzeFaces(bitmap512)
        } else {
            null
        }
        val ocrResult = if (bitmap512 != null) {
            mlAnalyzer.evaluateOcrGuardrail(bitmap512, row.displayName)
        } else {
            null
        }
        bitmap512?.recycle()

        val sharpness = visionMetrics?.sharpnessScore ?: 50f
        val exposure = visionMetrics?.exposureScore ?: 65f
        val faceScore = faceResult?.faceQualityScore ?: 65f

        val compositeScore = PureKotlinVisionEngine.computeCompositeBestShotScore(
            sharpnessScore = sharpness,
            faceQualityScore = faceScore,
            exposureScore = exposure,
            technicalFidelityScore = techFidelity
        )

        val isProtectedDoc = ocrResult?.isDocumentOrReceipt == true
        // CRITICAL OCR GUARDRAIL: Never mark an item as Blurry/Bad if isDocumentOrReceipt == true
        val isBlurryOrBad = !isProtectedDoc && !isScreenshot && (
            sharpness < 26f ||
                visionMetrics?.isUnderExposed == true ||
                visionMetrics?.isOverExposed == true
            )

        return MediaItemEntity(
            mediaStoreId = row.mediaStoreId,
            contentUri = row.contentUri,
            displayName = row.displayName,
            mimeType = row.mimeType,
            mediaType = MediaType.PHOTO,
            bucketName = row.bucketName,
            relativePath = row.relativePath,
            sizeBytes = row.sizeBytes,
            width = row.width,
            height = row.height,
            durationMs = 0L,
            dateAddedSeconds = row.dateAddedSeconds,
            dateTakenMillis = row.dateTakenMillis,
            generationModified = row.generationModified,
            partialSha256 = partialHash,
            dHash = visionMetrics?.dHash,
            isAnalyzed = true,
            sharpnessScore = sharpness,
            laplacianVarianceRaw = visionMetrics?.laplacianVarianceRaw ?: 0f,
            exposureScore = exposure,
            meanLuminance = visionMetrics?.meanLuminance ?: 128f,
            isUnderExposed = visionMetrics?.isUnderExposed ?: false,
            isOverExposed = visionMetrics?.isOverExposed ?: false,
            faceQualityScore = faceScore,
            faceCount = faceResult?.faceCount ?: 0,
            leftEyeOpenProb = faceResult?.leftEyeOpenProb ?: 0f,
            rightEyeOpenProb = faceResult?.rightEyeOpenProb ?: 0f,
            smilingProb = faceResult?.smilingProb ?: 0f,
            technicalFidelityScore = techFidelity,
            compositeBestShotScore = compositeScore,
            isDocumentOrReceipt = isProtectedDoc,
            ocrTextBlockCount = ocrResult?.textBlockCount ?: 0,
            ocrDetectedSnippet = ocrResult?.detectedSummary,
            isScreenshot = isScreenshot,
            isSocialMediaClutter = isSocialMedia,
            isBlurryOrBad = isBlurryOrBad,
            triageStatus = preservedTriageStatus,
            monthYearKey = monthYear
        )
    }

    private suspend fun promoteToForegroundSafely(
        scanned: Int,
        total: Int,
        clutterBytes: Long,
        isPaused: Boolean
    ) {
        runCatching {
            val foregroundInfo = createForegroundInfo(scanned, total, clutterBytes, isPaused)
            setForeground(foregroundInfo)
        }
    }

    private fun createForegroundInfo(
        scanned: Int,
        total: Int,
        clutterBytes: Long,
        isPaused: Boolean
    ): ForegroundInfo {
        val percent = if (total > 0) (scanned * 100) / total else 0
        val contentText = if (total > 0) {
            "Scanning Gallery: $scanned / $total ($percent%) • ${formatBytes(clutterBytes)} clutter found"
        } else {
            "Indexing MediaStore & checking generation delta..."
        }

        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseOrResumeAction = if (isPaused) {
            val resumeIntent = Intent(appContext, ScanNotificationActionReceiver::class.java).apply {
                action = ACTION_RESUME_SCAN
            }
            val resumePi = PendingIntent.getBroadcast(
                appContext,
                1,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                appContext.getString(R.string.action_resume),
                resumePi
            ).build()
        } else {
            val pauseIntent = Intent(appContext, ScanNotificationActionReceiver::class.java).apply {
                action = ACTION_PAUSE_SCAN
            }
            val pausePi = PendingIntent.getBroadcast(
                appContext,
                2,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                appContext.getString(R.string.action_pause),
                pausePi
            ).build()
        }

        val cancelIntent = Intent(appContext, ScanNotificationActionReceiver::class.java).apply {
            action = ACTION_CANCEL_SCAN
        }
        val cancelPi = PendingIntent.getBroadcast(
            appContext,
            3,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            appContext.getString(R.string.action_cancel),
            cancelPi
        ).build()

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(appContext.getString(R.string.scan_notification_title))
            .setContentText(contentText)
            .setProgress(maxOf(1, total), scanned, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(pauseOrResumeAction)
            .addAction(cancelAction)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = appContext.getString(R.string.notification_channel_desc)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
