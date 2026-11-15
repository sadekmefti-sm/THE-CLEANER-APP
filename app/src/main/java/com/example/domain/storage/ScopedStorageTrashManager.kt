package com.example.domain.storage

import android.app.PendingIntent
import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.MediaItemEntity

data class TrashBatch(
    val mediaIds: List<Long>,
    val uris: List<Uri>,
    val totalBytes: Long
)

/**
 * Android 11–15+ (minSdk 30) Scoped Storage Staging Trash Bin Manager:
 * - Never triggers an OS popup on individual card swipes.
 * - Splits staged items into batches of at most 1,000 URIs so Binder transaction limits are never exceeded.
 * - Sends every batch through MediaStore.createTrashRequest(contentResolver, uris, true)
 *   so files safely move to the Android system 30-day Trash bin.
 */
object ScopedStorageTrashManager {

    private const val TAG = "ScopedStorageTrashMgr"
    const val MAX_TRASH_BATCH_SIZE = 1000

    fun chunkStagedItemsIntoBatches(
        stagedItems: List<MediaItemEntity>,
        maxBatchSize: Int = MAX_TRASH_BATCH_SIZE
    ): List<TrashBatch> {
        if (stagedItems.isEmpty()) return emptyList()
        return stagedItems.chunked(maxBatchSize).map { chunk ->
            TrashBatch(
                mediaIds = chunk.map { it.mediaStoreId },
                uris = chunk.map { Uri.parse(it.contentUri) },
                totalBytes = chunk.sumOf { it.sizeBytes }
            )
        }
    }

    /**
     * Creates a batched system IntentSender PendingIntent to move [uris] into Android's
     * 30-day system Trash bin.
     */
    fun createBatchTrashPendingIntent(
        contentResolver: ContentResolver,
        uris: List<Uri>
    ): Result<PendingIntent> {
        if (uris.isEmpty()) {
            return Result.failure(IllegalArgumentException("Cannot create trash request for empty URI list"))
        }
        return runCatching {
            MediaStore.createTrashRequest(contentResolver, uris, true)
        }.onFailure { e ->
            Log.e(TAG, "Failed to create MediaStore trash request for ${uris.size} items", e)
        }
    }
}
