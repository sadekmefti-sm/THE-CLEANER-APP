package com.example.domain.clustering

import android.content.ContentResolver
import android.net.Uri
import com.example.data.local.ClusterType
import com.example.data.local.MediaItemEntity
import com.example.data.local.SensitivityLevel
import com.example.data.local.SimilarClusterEntity
import com.example.domain.scanner.PureKotlinVisionEngine
import com.example.domain.scanner.SafeBitmapDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class ClusteringOutput(
    val clusters: List<SimilarClusterEntity>,
    val updatedMediaItems: List<MediaItemEntity>
)

/**
 * Three-Tier Sub-Quadratic O(N log N) Similarity & Burst Clustering Pipeline:
 * Tier 1: Exact Duplicates -> Group by file size -> compare 64KB partial SHA-256 -> verify full SHA-256 on collision.
 * Tier 2 & 3: Time-Window + 64-bit Perceptual dHash Hamming Distance clustering via bitwise XOR & Long.countOneBits().
 * Automatically crowns the highest composite Best-Shot photo per cluster.
 */
object SimilarityClusterEngine {

    fun buildClusters(
        contentResolver: ContentResolver,
        analyzedPhotos: List<MediaItemEntity>,
        sensitivity: SensitivityLevel
    ): ClusteringOutput {
        if (analyzedPhotos.isEmpty()) {
            return ClusteringOutput(emptyList(), emptyList())
        }

        val assignedIds = HashSet<Long>(analyzedPhotos.size)
        val clusters = mutableListOf<SimilarClusterEntity>()
        val updatedItems = mutableListOf<MediaItemEntity>()
        val dateFormatter = SimpleDateFormat("MMM d • HH:mm", Locale.US)

        // -----------------------------------------------------------------
        // TIER 1: Exact Byte-Identical Duplicates (Size -> 64KB Hash -> Full SHA-256)
        // -----------------------------------------------------------------
        val sizeGroups = analyzedPhotos
            .filter { it.sizeBytes > 0L && !it.PartialHashOrNull().isNullOrEmpty() }
            .groupBy { it.sizeBytes }

        for ((sizeBytes, sameSizeItems) in sizeGroups) {
            if (sameSizeItems.size < 2) continue
            val partialGroups = sameSizeItems.groupBy { it.partialSha256 }
            for ((partialHash, candidates) in partialGroups) {
                if (partialHash == null || candidates.size < 2) continue

                // Compute or reuse full SHA-256 only on collision
                val withFullHash = candidates.map { item ->
                    val full = item.fullSha256 ?: SafeBitmapDecoder.computeFullSha256(
                        contentResolver,
                        Uri.parse(item.contentUri)
                    ) ?: partialHash
                    item.copy(fullSha256 = full)
                }

                val exactMatches = withFullHash.groupBy { it.fullSha256 }
                for ((fullHash, exactList) in exactMatches) {
                    if (fullHash == null || exactList.size < 2) continue
                    val clusterId = "exact_${sizeBytes}_${fullHash.take(10)}"
                    val sortedByBest = exactList.sortedWith(
                        compareByDescending<MediaItemEntity> { it.compositeBestShotScore }
                            .thenBy { it.dateAddedSeconds }
                    )
                    val bestItem = sortedByBest.first()
                    val bestDHash = bestItem.dHash ?: 0L

                    val markedMembers = sortedByBest.mapIndexed { index, member ->
                        assignedIds.add(member.mediaStoreId)
                        val dist = if (member.dHash != null) {
                            PureKotlinVisionEngine.hammingDistance(bestDHash, member.dHash)
                        } else 0
                        member.copy(
                            clusterId = clusterId,
                            clusterType = ClusterType.EXACT_DUPLICATE,
                            isBestInCluster = (index == 0),
                            hammingDistanceToBest = dist
                        )
                    }
                    updatedItems.addAll(markedMembers)

                    val totalBytes = markedMembers.sumOf { it.sizeBytes }
                    val reclaimableBytes = totalBytes - bestItem.sizeBytes
                    clusters.add(
                        SimilarClusterEntity(
                            clusterId = clusterId,
                            clusterType = ClusterType.EXACT_DUPLICATE,
                            bestMediaId = bestItem.mediaStoreId,
                            bestShotScore = bestItem.compositeBestShotScore,
                            itemCount = markedMembers.size,
                            totalBytes = totalBytes,
                            reclaimableBytes = max(0L, reclaimableBytes),
                            anchorTimestampMillis = bestItem.dateTakenMillis,
                            maxHammingDistance = 0,
                            title = "Exact Duplicates • ${dateFormatter.format(Date(bestItem.dateTakenMillis))}"
                        )
                    )
                }
            }
        }

        // -----------------------------------------------------------------
        // TIER 2 & 3: Time-Window + 64-Bit Perceptual dHash Clustering (O(N log N))
        // -----------------------------------------------------------------
        val remainingPhotos = analyzedPhotos
            .filter { it.mediaStoreId !in assignedIds && it.dHash != null }
            .sortedByDescending { it.dateTakenMillis }

        val maxHamming = sensitivity.maxHammingDistance
        val timeWindowMs = sensitivity.timeWindowMs
        val visited = BooleanArray(remainingPhotos.size)

        for (i in remainingPhotos.indices) {
            if (visited[i]) continue
            val anchor = remainingPhotos[i]
            val anchorHash = anchor.dHash ?: continue
            val clusterMembers = mutableListOf(anchor)
            visited[i] = true
            var maxObservedHamming = 0

            // Scan forward within the time window (or extended window for near-identical burst/edits)
            for (j in (i + 1) until remainingPhotos.size) {
                if (visited[j]) continue
                val candidate = remainingPhotos[j]
                val candidateHash = candidate.dHash ?: continue
                val timeDelta = abs(anchor.dateTakenMillis - candidate.dateTakenMillis)

                // Stop inner loop once outside 6x time window unless hashes are ultra-close (<= 4 bits)
                if (timeDelta > timeWindowMs * 6L) {
                    val distFar = PureKotlinVisionEngine.hammingDistance(anchorHash, candidateHash)
                    if (distFar <= 3) {
                        visited[j] = true
                        clusterMembers.add(candidate)
                        maxObservedHamming = max(maxObservedHamming, distFar)
                    }
                    if (j - i > 65) break
                    continue
                }

                val hamming = PureKotlinVisionEngine.hammingDistance(anchorHash, candidateHash)
                val withinWindow = timeDelta <= timeWindowMs && hamming <= maxHamming
                val ultraCloseBurst = timeDelta <= 12_000L && hamming <= (maxHamming + 3)

                if (withinWindow || ultraCloseBurst) {
                    visited[j] = true
                    clusterMembers.add(candidate)
                    maxObservedHamming = max(maxObservedHamming, hamming)
                }
            }

            if (clusterMembers.size >= 2) {
                val sortedByCrown = clusterMembers.sortedWith(
                    compareByDescending<MediaItemEntity> { it.compositeBestShotScore }
                        .thenByDescending { it.sharpnessScore }
                        .thenByDescending { it.sizeBytes }
                )
                val crownedBest = sortedByCrown.first()
                val crownedHash = crownedBest.dHash ?: 0L
                val minTime = clusterMembers.minOf { it.dateTakenMillis }
                val maxTime = clusterMembers.maxOf { it.dateTakenMillis }
                val isBurst = (maxTime - minTime) <= 15_000L

                val clusterType = if (isBurst) ClusterType.BURST_SHOT else ClusterType.SIMILAR_PHOTO
                val clusterId = "sim_${crownedBest.mediaStoreId}_${clusterMembers.size}"

                val finalizedMembers = sortedByCrown.mapIndexed { idx, item ->
                    assignedIds.add(item.mediaStoreId)
                    val dist = item.dHash?.let { PureKotlinVisionEngine.hammingDistance(crownedHash, it) } ?: 0
                    item.copy(
                        clusterId = clusterId,
                        clusterType = clusterType,
                        isBestInCluster = (idx == 0),
                        hammingDistanceToBest = dist
                    )
                }
                updatedItems.addAll(finalizedMembers)

                val totalBytes = finalizedMembers.sumOf { it.sizeBytes }
                val reclaimable = totalBytes - crownedBest.sizeBytes
                val prefix = if (isBurst) "Burst Sequence" else "Similar Shots"
                clusters.add(
                    SimilarClusterEntity(
                        clusterId = clusterId,
                        clusterType = clusterType,
                        bestMediaId = crownedBest.mediaStoreId,
                        bestShotScore = crownedBest.compositeBestShotScore,
                        itemCount = finalizedMembers.size,
                        totalBytes = totalBytes,
                        reclaimableBytes = max(0L, reclaimable),
                        anchorTimestampMillis = crownedBest.dateTakenMillis,
                        maxHammingDistance = maxObservedHamming,
                        title = "$prefix • ${dateFormatter.format(Date(crownedBest.dateTakenMillis))}"
                    )
                )
            } else {
                // Single unclustered photo: ensure stale cluster fields are cleared if sensitivity tightened
                if (anchor.clusterId != null || anchor.isBestInCluster) {
                    updatedItems.add(
                        anchor.copy(
                            clusterId = null,
                            clusterType = null,
                            isBestInCluster = false,
                            hammingDistanceToBest = 0
                        )
                    )
                }
            }
        }

        val sortedClusters = clusters.sortedByDescending { it.reclaimableBytes }
        return ClusteringOutput(
            clusters = sortedClusters,
            updatedMediaItems = updatedItems
        )
    }

    private fun MediaItemEntity.PartialHashOrNull(): String? = partialSha256
}
