package com.example.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class MediaType {
    PHOTO,
    VIDEO
}

enum class TriageStatus {
    UNREVIEWED,
    KEPT,
    STAGED_FOR_TRASH,
    FAVORITED,
    COMPRESSED_REPLACED
}

enum class ClusterType {
    EXACT_DUPLICATE,
    BURST_SHOT,
    SIMILAR_PHOTO
}

enum class ScanMode(val label: String, val maxInitialItems: Int) {
    QUICK_BATCH("Quick Batch (500 Instant)", 500),
    FULL_DEEP_SCAN("Full Deep Scan", Int.MAX_VALUE)
}

enum class SensitivityLevel(val label: String, val maxHammingDistance: Int, val timeWindowMs: Long) {
    STRICT("Strict (≤ 4 bits)", 4, 45_000L),
    BALANCED("Balanced (≤ 8 bits)", 8, 180_000L),
    LOOSE("Loose (≤ 12 bits)", 12, 600_000L)
}

enum class SmartCategory(val title: String, val subtitle: String) {
    SIMILAR_CLUSTERS("Similar & Burst Clusters", "Crowned Best Shots & near-duplicates"),
    HEAVY_VIDEOS("Heavy 4K/1080p Videos", "Shrink up to 80% with Media3 H.265"),
    OCR_DOCUMENTS("Documents, IDs & Receipts", "OCR-protected from blurry deletion"),
    SCREENSHOTS_SOCIAL("Screenshots & Social Media", "WhatsApp, screen captures & memes"),
    BLURRY_EXPOSURE("Blurry & Dark Pocket Shots", "Low Laplacian sharpness or blown exposure"),
    MICRO_CHAPTERS("Monthly Micro-Chapters", "Bite-sized chronological batches")
}

@Immutable
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["clusterId"]),
        Index(value = ["triageStatus"]),
        Index(value = ["mediaType"]),
        Index(value = ["isDocumentOrReceipt"]),
        Index(value = ["isScreenshot"]),
        Index(value = ["isBlurryOrBad"]),
        Index(value = ["dateTakenMillis"]),
        Index(value = ["sizeBytes"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val mediaStoreId: Long,
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
    val generationModified: Long,
    // Hashing & Perceptual Similarity
    val partialSha256: String? = null,
    val fullSha256: String? = null,
    val dHash: Long? = null,
    val isAnalyzed: Boolean = false,
    // Pure Kotlin & ML Kit Quality Metrics (0..100 normalized)
    val sharpnessScore: Float = 0f,
    val laplacianVarianceRaw: Float = 0f,
    val exposureScore: Float = 0f,
    val meanLuminance: Float = 128f,
    val isUnderExposed: Boolean = false,
    val isOverExposed: Boolean = false,
    val faceQualityScore: Float = 50f,
    val faceCount: Int = 0,
    val leftEyeOpenProb: Float = 0f,
    val rightEyeOpenProb: Float = 0f,
    val smilingProb: Float = 0f,
    val technicalFidelityScore: Float = 50f,
    val compositeBestShotScore: Float = 0f,
    // OCR Guardrail & Smart Categorization
    val isDocumentOrReceipt: Boolean = false,
    val ocrTextBlockCount: Int = 0,
    val ocrDetectedSnippet: String? = null,
    val isScreenshot: Boolean = false,
    val isSocialMediaClutter: Boolean = false,
    val isBlurryOrBad: Boolean = false,
    // Clustering & Crown State
    val clusterId: String? = null,
    val clusterType: ClusterType? = null,
    val isBestInCluster: Boolean = false,
    val hammingDistanceToBest: Int = 0,
    // Triage & Compression Tracking
    val triageStatus: TriageStatus = TriageStatus.UNREVIEWED,
    val compressedBytesSaved: Long = 0L,
    val monthYearKey: String = ""
)

@Immutable
@Entity(
    tableName = "similar_clusters",
    indices = [Index(value = ["reclaimableBytes"])]
)
data class SimilarClusterEntity(
    @PrimaryKey val clusterId: String,
    val clusterType: ClusterType,
    val bestMediaId: Long,
    val bestShotScore: Float,
    val itemCount: Int,
    val totalBytes: Long,
    val reclaimableBytes: Long,
    val anchorTimestampMillis: Long,
    val maxHammingDistance: Int,
    val title: String
)

@Immutable
@Entity(tableName = "scan_telemetry")
data class ScanTelemetryEntity(
    @PrimaryKey val id: Int = 1,
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val scanMode: ScanMode = ScanMode.QUICK_BATCH,
    val scannedCount: Int = 0,
    val totalLibraryCount: Int = 0,
    val clutterBytesFound: Long = 0L,
    val lifetimeBytesReclaimed: Long = 0L,
    val lastScanTimestampMillis: Long = 0L,
    val statusMessage: String = "Ready to scan"
)

class RoomTypeConverters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType =
        runCatching { MediaType.valueOf(value) }.getOrDefault(MediaType.PHOTO)

    @TypeConverter
    fun fromTriageStatus(value: TriageStatus): String = value.name

    @TypeConverter
    fun toTriageStatus(value: String): TriageStatus =
        runCatching { TriageStatus.valueOf(value) }.getOrDefault(TriageStatus.UNREVIEWED)

    @TypeConverter
    fun fromClusterType(value: ClusterType?): String? = value?.name

    @TypeConverter
    fun toClusterType(value: String?): ClusterType? =
        value?.let { runCatching { ClusterType.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromScanMode(value: ScanMode): String = value.name

    @TypeConverter
    fun toScanMode(value: String): ScanMode =
        runCatching { ScanMode.valueOf(value) }.getOrDefault(ScanMode.QUICK_BATCH)
}
