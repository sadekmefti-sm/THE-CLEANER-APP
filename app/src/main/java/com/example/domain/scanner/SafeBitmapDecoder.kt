package com.example.domain.scanner

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.max

/**
 * Memory-safe bitmap decoder and fast two-stage cryptographic hash utility.
 * Never loads full-resolution 50MP bitmaps into heap memory.
 */
object SafeBitmapDecoder {

    /**
     * Decodes a downsampled bitmap strictly bounded near [targetDimension] x [targetDimension]
     * using a two-pass BitmapFactory.Options decode with inJustDecodeBounds = true.
     *
     * - Use 256 for dHash and 3x3 Laplacian variance / luminance histogram
     * - Use 512 for ML Kit Face Detection & Text Recognition (OCR)
     */
    fun decodeSubsampledBitmap(
        contentResolver: ContentResolver,
        uri: Uri,
        targetDimension: Int
    ): Bitmap? {
        return runCatching {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val rawWidth = boundsOptions.outWidth
            val rawHeight = boundsOptions.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) return null

            val sampleSize = calculateInSampleSize(rawWidth, rawHeight, targetDimension, targetDimension)
            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            }

            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        }.getOrNull()
    }

    fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    /**
     * Fast Tier-1 Exact Duplicate check:
     * Computes SHA-256 over the first 64KB + file size + last 64KB (if seekable/available).
     * Avoids reading multi-gigabyte 4K videos unless two files have identical byte size.
     */
    fun computePartial64KbHash(
        contentResolver: ContentResolver,
        uri: Uri,
        sizeBytes: Long
    ): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            // Mix file size into the digest header
            digest.update(sizeBytes.toString().toByteArray())

            val chunkSize = 64 * 1024
            val buffer = ByteArray(chunkSize)

            contentResolver.openInputStream(uri)?.use { stream ->
                val headRead = readUpTo(stream, buffer, chunkSize)
                if (headRead > 0) {
                    digest.update(buffer, 0, headRead)
                }
                if (sizeBytes > chunkSize * 2L) {
                    val toSkip = sizeBytes - headRead - chunkSize
                    if (toSkip > 0) {
                        skipFully(stream, toSkip)
                    }
                    val tailRead = readUpTo(stream, buffer, chunkSize)
                    if (tailRead > 0) {
                        digest.update(buffer, 0, tailRead)
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    /**
     * Full SHA-256 computed ONLY when both file size and 64KB partial hash collide.
     */
    fun computeFullSha256(
        contentResolver: ContentResolver,
        uri: Uri
    ): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32 * 1024)
            contentResolver.openInputStream(uri)?.use { stream ->
                var read = stream.read(buffer)
                while (read > 0) {
                    digest.update(buffer, 0, read)
                    read = stream.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun readUpTo(stream: InputStream, buffer: ByteArray, maxBytes: Int): Int {
        var total = 0
        while (total < maxBytes) {
            val count = stream.read(buffer, total, maxBytes - total)
            if (count <= 0) break
            total += count
        }
        return total
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }
}
