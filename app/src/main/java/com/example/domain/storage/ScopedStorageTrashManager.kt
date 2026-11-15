package com.example.domain.storage

import android.app.PendingIntent
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Android 11–15+ Scoped Storage Staging Trash Bin Manager:
 * - Never triggers an OS popup on individual card swipes.
 * - Batches all staged URIs into a single MediaStore.createTrashRequest(contentResolver, uris, true)
 *   so files safely move to the Android system 30-day Trash bin.
 */
object ScopedStorageTrashManager {

    /**
     * Creates a single batched system IntentSender PendingIntent to move [uris] into Android's
     * 30-day system Trash bin (API 30+), or Delete request on API 29.
     */
    fun createBatchTrashPendingIntent(
        contentResolver: ContentResolver,
        uris: List<Uri>
    ): PendingIntent? {
        if (uris.isEmpty()) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStore.createTrashRequest(contentResolver, uris, true)
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * Fallback direct removal for older Android API 24–29 devices or app-owned synthetic demo media.
     */
    fun deleteDirectlyIfPermitted(
        contentResolver: ContentResolver,
        uris: List<Uri>
    ): Int {
        var deletedCount = 0
        for (uri in uris) {
            runCatching {
                val rows = contentResolver.delete(uri, null, null)
                if (rows > 0) deletedCount += rows
            }
        }
        return deletedCount
    }
}
