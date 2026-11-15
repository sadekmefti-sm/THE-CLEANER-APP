package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class GenerationRecord(
    val mediaStoreId: Long,
    val generationModified: Long,
    val isAnalyzed: Boolean,
    val triageStatus: TriageStatus
)

@Dao
interface MediaDao {

    @Query("SELECT * FROM media_items ORDER BY dateTakenMillis DESC")
    fun observeAllMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE triageStatus = 'UNREVIEWED' AND mediaType = 'PHOTO' ORDER BY sizeBytes DESC")
    fun observeSwipePhotoDeck(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE mediaType = 'VIDEO' AND triageStatus != 'STAGED_FOR_TRASH' ORDER BY sizeBytes DESC")
    fun observeVideoTriageFeed(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE triageStatus = 'STAGED_FOR_TRASH' ORDER BY sizeBytes DESC")
    fun observeStagedTrashItems(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isDocumentOrReceipt = 1 AND triageStatus != 'STAGED_FOR_TRASH' ORDER BY dateTakenMillis DESC")
    fun observeProtectedDocuments(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE (isScreenshot = 1 OR isSocialMediaClutter = 1) AND isDocumentOrReceipt = 0 AND triageStatus != 'STAGED_FOR_TRASH' ORDER BY sizeBytes DESC")
    fun observeScreenshotsAndSocial(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isBlurryOrBad = 1 AND isDocumentOrReceipt = 0 AND triageStatus != 'STAGED_FOR_TRASH' ORDER BY sharpnessScore ASC")
    fun observeBlurryAndBadShots(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM similar_clusters ORDER BY reclaimableBytes DESC")
    fun observeSimilarClusters(): Flow<List<SimilarClusterEntity>>

    @Query("SELECT * FROM media_items WHERE clusterId = :clusterId ORDER BY isBestInCluster DESC, compositeBestShotScore DESC")
    fun observeClusterMembers(clusterId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE clusterId IS NOT NULL ORDER BY clusterId, isBestInCluster DESC, compositeBestShotScore DESC")
    fun observeAllClusteredItems(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM scan_telemetry WHERE id = 1")
    fun observeScanTelemetry(): Flow<ScanTelemetryEntity?>

    @Query("SELECT * FROM scan_telemetry WHERE id = 1")
    suspend fun getScanTelemetry(): ScanTelemetryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScanTelemetry(telemetry: ScanTelemetryEntity)

    @Query("SELECT mediaStoreId, generationModified, isAnalyzed, triageStatus FROM media_items")
    suspend fun getAllGenerationRecords(): List<GenerationRecord>

    @Query("SELECT * FROM media_items WHERE mediaType = 'PHOTO' AND isAnalyzed = 1 ORDER BY dateTakenMillis DESC")
    suspend fun getAnalyzedPhotosForClustering(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE mediaStoreId = :mediaId LIMIT 1")
    suspend fun getMediaById(mediaId: Long): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaBatchInternal(items: List<MediaItemEntity>)

    /**
     * Chunked transaction in batches of 100 items to keep SQLite WAL lock contention zero
     * and preserve 60/120fps Compose scrolling while scanning.
     */
    @Transaction
    suspend fun upsertMediaChunked(items: List<MediaItemEntity>, chunkSize: Int = 100) {
        items.chunked(chunkSize).forEach { batch ->
            upsertMediaBatchInternal(batch)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClusters(clusters: List<SimilarClusterEntity>)

    @Query("DELETE FROM similar_clusters")
    suspend fun clearAllClusters()

    @Transaction
    suspend fun replaceAllClusters(
        clusters: List<SimilarClusterEntity>,
        updatedItems: List<MediaItemEntity>
    ) {
        clearAllClusters()
        if (clusters.isNotEmpty()) {
            insertClusters(clusters)
        }
        if (updatedItems.isNotEmpty()) {
            upsertMediaChunked(updatedItems, 100)
        }
    }

    @Query("UPDATE media_items SET triageStatus = :status WHERE mediaStoreId = :mediaId")
    suspend fun updateTriageStatus(mediaId: Long, status: TriageStatus)

    @Query("UPDATE media_items SET triageStatus = :status WHERE mediaStoreId IN (:mediaIds)")
    suspend fun updateTriageStatusBatch(mediaIds: List<Long>, status: TriageStatus)

    @Transaction
    suspend fun crownBestShotInCluster(clusterId: String, newBestMediaId: Long) {
        clearCrownForCluster(clusterId)
        setCrownForMedia(newBestMediaId)
    }

    @Query("UPDATE media_items SET isBestInCluster = 0 WHERE clusterId = :clusterId")
    suspend fun clearCrownForCluster(clusterId: String)

    @Query("UPDATE media_items SET isBestInCluster = 1, triageStatus = 'KEPT' WHERE mediaStoreId = :mediaId")
    suspend fun setCrownForMedia(mediaId: Long)

    @Query("UPDATE media_items SET sizeBytes = :newSizeBytes, width = :newWidth, height = :newHeight, compressedBytesSaved = compressedBytesSaved + :bytesSaved, triageStatus = 'KEPT' WHERE mediaStoreId = :mediaId")
    suspend fun recordCompressionSuccess(
        mediaId: Long,
        newSizeBytes: Long,
        newWidth: Int,
        newHeight: Int,
        bytesSaved: Long
    )

    @Query("DELETE FROM media_items WHERE mediaStoreId IN (:mediaIds)")
    suspend fun deleteMediaByIds(mediaIds: List<Long>)

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getTotalCount(): Int
}
