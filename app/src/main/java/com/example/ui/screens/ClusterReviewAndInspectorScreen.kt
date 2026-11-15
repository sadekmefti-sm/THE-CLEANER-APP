package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.MediaItemEntity
import com.example.data.local.SmartCategory
import com.example.data.local.TriageStatus
import com.example.ui.theme.CompressionCyan
import com.example.ui.theme.CrownAmberGold
import com.example.ui.theme.KeepMintGreen
import com.example.ui.theme.OcrEmeraldShield
import com.example.ui.theme.TrashCoralRed
import com.example.ui.viewmodel.CleanVaultUiState
import com.example.ui.viewmodel.CleanVaultViewModel
import java.util.Locale

@Composable
fun SimilarClusterReviewScreen(
    uiState: CleanVaultUiState,
    onSelectCluster: (String) -> Unit,
    onCrownManualBestShot: (String, Long) -> Unit,
    onKeepBestAndStageRest: (String) -> Unit,
    onOpenSyncInspector: (MediaItemEntity, MediaItemEntity) -> Unit,
    onStageSingleItem: (MediaItemEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val clusters = uiState.similarClusters
    val activeClusterId = uiState.selectedClusterId ?: clusters.firstOrNull()?.clusterId
    val activeMembers = activeClusterId?.let { uiState.clusterMembersMap[it] }.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("cluster_review_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Similar & Burst Cluster Crown",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Score = 0.35·Sharpness + 0.35·Face + 0.15·Exposure + 0.15·Fidelity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (clusters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No similar or burst photo clusters detected at current sensitivity. Try switching sensitivity to Loose (≤ 12 bits) or seed demo media.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        // Cluster Selector Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(clusters, key = { it.clusterId }) { cluster ->
                val isSelected = cluster.clusterId == activeClusterId
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCluster(cluster.clusterId) },
                    label = {
                        Text(
                            text = "${cluster.title} (${cluster.itemCount}) • Save ${CleanVaultViewModel.formatStorageSize(cluster.reclaimableBytes)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }

        // Sticky "Keep Best & Stage Rest" Bar
        if (activeClusterId != null && activeMembers.size >= 2) {
            val bestShot = activeMembers.firstOrNull { it.isBestInCluster } ?: activeMembers.first()
            val secondShot = activeMembers.firstOrNull { it.mediaStoreId != bestShot.mediaStoreId } ?: bestShot

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onKeepBestAndStageRest(activeClusterId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrownAmberGold,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cluster_keep_best_trash_rest_button")
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Keep Best & Trash Rest", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onOpenSyncInspector(bestShot, secondShot) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("cluster_sync_zoom_button")
                    ) {
                        Icon(Icons.Default.Compare, contentDescription = "Sync Zoom", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Zoom")
                    }
                }
            }
        }

        // Cluster Member Cards with Crown & Telemetry Breakdown
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(activeMembers, key = { it.mediaStoreId }) { item ->
                ClusterMemberTelemetryCard(
                    item = item,
                    onCrownAsBest = {
                        if (activeClusterId != null) {
                            onCrownManualBestShot(activeClusterId, item.mediaStoreId)
                        }
                    },
                    onStageTrash = { onStageSingleItem(item) }
                )
            }
        }
    }
}

@Composable
private fun ClusterMemberTelemetryCard(
    item: MediaItemEntity,
    onCrownAsBest: () -> Unit,
    onStageTrash: () -> Unit
) {
    val borderColor = when {
        item.isBestInCluster -> CrownAmberGold
        item.triageStatus == TriageStatus.STAGED_FOR_TRASH -> TrashCoralRed
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = Uri.parse(item.contentUri),
                        contentDescription = item.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (item.isBestInCluster) {
                        Surface(
                            color = CrownAmberGold,
                            shape = RoundedCornerShape(bottomEnd = 10.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "Best Shot",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "BEST SHOT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(Locale.US, "Score %.1f / 100", item.compositeBestShotScore),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (item.isBestInCluster) CrownAmberGold else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "dHash Δ ${item.hammingDistanceToBest}b",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "${item.displayName} • ${CleanVaultViewModel.formatStorageSize(item.sizeBytes)} • ${item.width}x${item.height}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    MetricMiniBar("Sharpness (35%)", item.sharpnessScore, CompressionCyan)
                    MetricMiniBar("Face & Eyes (35%)", item.faceQualityScore, CrownAmberGold)
                    MetricMiniBar("Exposure (15%)", item.exposureScore, KeepMintGreen)
                    MetricMiniBar("Fidelity (15%)", item.technicalFidelityScore, MaterialTheme.colorScheme.primary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!item.isBestInCluster) {
                    OutlinedButton(
                        onClick = onCrownAsBest,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("crown_manual_best_${item.mediaStoreId}")
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crown Best Shot")
                    }
                }
                if (item.triageStatus != TriageStatus.STAGED_FOR_TRASH) {
                    OutlinedButton(
                        onClick = onStageTrash,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TrashCoralRed),
                        border = BorderStroke(1.dp, TrashCoralRed.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("stage_trash_${item.mediaStoreId}")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stage Trash")
                    }
                } else {
                    Surface(
                        color = TrashCoralRed.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "STAGED FOR TRASH BIN",
                                style = MaterialTheme.typography.labelMedium,
                                color = TrashCoralRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricMiniBar(label: String, score: Float, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(108.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0.04f, 1f) },
            color = color,
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${score.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(26.dp)
        )
    }
}

/**
 * Synchronized Side-by-Side Pinch-to-Zoom & Pan Comparison Inspector
 * so users can verify eye openness and sharpness between two burst shots simultaneously.
 */
@Composable
fun SynchronizedZoomInspectorScreen(
    primary: MediaItemEntity,
    comparison: MediaItemEntity,
    onCrownBestShot: (MediaItemEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var sharedScale by remember { mutableFloatStateOf(1f) }
    var sharedOffset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (sharedScale * zoomChange).coerceIn(1f, 5f)
        sharedScale = newScale
        sharedOffset = if (newScale > 1f) {
            sharedOffset + panChange
        } else {
            Offset.Zero
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Synchronized Pinch-to-Zoom Inspector",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Pinch or drag either photo to inspect both at ${String.format(Locale.US, "%.1fx", sharedScale)} zoom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    sharedScale = 1f
                    sharedOffset = Offset.Zero
                }
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset 1x")
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ZoomPaneCard(
                item = primary,
                scale = sharedScale,
                offset = sharedOffset,
                transformState = transformState,
                onCrownClick = { onCrownBestShot(primary) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            ZoomPaneCard(
                item = comparison,
                scale = sharedScale,
                offset = sharedOffset,
                transformState = transformState,
                onCrownClick = { onCrownBestShot(comparison) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun ZoomPaneCard(
    item: MediaItemEntity,
    scale: Float,
    offset: Offset,
    transformState: androidx.compose.foundation.gestures.TransformableState,
    onCrownClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (item.isBestInCluster) 2.dp else 1.dp,
            color = if (item.isBestInCluster) CrownAmberGold else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color.Black)
                    .transformable(state = transformState)
            ) {
                AsyncImage(
                    model = Uri.parse(item.contentUri),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "Total Score: %.1f", item.compositeBestShotScore),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (item.isBestInCluster) CrownAmberGold else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Sharpness ${item.sharpnessScore.toInt()} • Face ${item.faceQualityScore.toInt()} • Exp ${item.exposureScore.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onCrownClick,
                    enabled = !item.isBestInCluster,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(if (item.isBestInCluster) "Crowned Best Shot" else "Crown This Shot")
                }
            }
        }
    }
}

/**
 * Smart Category Detail List Screen:
 * Supports OCR-Protected Documents Vault, Screenshots & Social Clutter, Blurry/Dark Pocket Shots,
 * and Monthly Micro-Chapters.
 */
@Composable
fun CategoryMediaListScreen(
    uiState: CleanVaultUiState,
    onStageAllForTrash: (List<MediaItemEntity>) -> Unit,
    onStageSingleItem: (MediaItemEntity) -> Unit,
    onOpenCompressor: (MediaItemEntity) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val category = uiState.selectedCategory
    val items = when (category) {
        SmartCategory.OCR_DOCUMENTS -> uiState.protectedDocuments
        SmartCategory.SCREENSHOTS_SOCIAL -> uiState.screenshotsAndSocial
        SmartCategory.BLURRY_EXPOSURE -> uiState.blurryAndBadShots
        SmartCategory.MICRO_CHAPTERS -> uiState.allMedia
        else -> uiState.allMedia
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("category_list_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (category != SmartCategory.OCR_DOCUMENTS && items.isNotEmpty()) {
                Button(
                    onClick = { onStageAllForTrash(items) },
                    colors = ButtonDefaults.buttonColors(containerColor = TrashCoralRed),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("category_stage_all_button")
                ) {
                    Text("Stage All (${items.size})")
                }
            }
        }

        if (category == SmartCategory.OCR_DOCUMENTS) {
            Surface(
                color = OcrEmeraldShield.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, OcrEmeraldShield.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "OCR Guardrail",
                        tint = OcrEmeraldShield
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "On-Device ML Kit Text Recognition Guardrail Active: These items contain structured text, receipts, invoices, or IDs and are automatically excluded from Blurry/Bad Quality auto-deletion.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.mediaStoreId }) { item ->
                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = Uri.parse(item.contentUri),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${CleanVaultViewModel.formatStorageSize(item.sizeBytes)} • ${item.width}x${item.height} • Sharpness ${item.sharpnessScore.toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.isDocumentOrReceipt) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = OcrEmeraldShield.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "OCR Blocks: ${item.ocrTextBlockCount} • ${item.ocrDetectedSnippet ?: "Protected Document"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OcrEmeraldShield,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { onOpenCompressor(item) },
                                modifier = Modifier.testTag("compress_item_${item.mediaStoreId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Compress,
                                    contentDescription = "Compress",
                                    tint = CompressionCyan
                                )
                            }
                            if (!item.isDocumentOrReceipt) {
                                IconButton(
                                    onClick = { onStageSingleItem(item) },
                                    modifier = Modifier.testTag("trash_item_${item.mediaStoreId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Stage Trash",
                                        tint = TrashCoralRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
