package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.domain.compression.CompressionExecutionResult
import com.example.domain.compression.CompressionPlan
import com.example.domain.compression.CompressionPreset
import com.example.ui.theme.CompressionCyan
import com.example.ui.theme.CrownAmberGold
import com.example.ui.theme.KeepMintGreen
import com.example.ui.theme.OcrEmeraldShield
import com.example.ui.theme.TrashCoralRed
import com.example.ui.viewmodel.CleanVaultUiState
import com.example.ui.viewmodel.CleanVaultViewModel
import kotlin.math.roundToInt

@Composable
fun PandaCompressorStudioScreen(
    uiState: CleanVaultUiState,
    compressionPlan: CompressionPlan?,
    onSelectMediaItem: (MediaItemEntity) -> Unit,
    onSelectPreset: (CompressionPreset) -> Unit,
    onSliderRatioChanged: (Float) -> Unit,
    onCustomTargetMbChanged: (String) -> Unit,
    onToggleWebpPhoto: (Boolean) -> Unit,
    onStartCompression: () -> Unit,
    onReplaceOriginalToTrash: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val studio = uiState.compressionStudio
    val activeItem = studio.selectedItem
    val candidateList = (uiState.videoTriageFeed + uiState.allMedia.filter { it.mediaType == MediaType.PHOTO })
        .distinctBy { it.mediaStoreId }
        .take(30)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("compressor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "Panda-Style Video & Photo Downscaler",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Media3 Transformer H.265/HEVC • Reverse Bitrate Math • EXIF Preserver",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Media Picker Carousel
            if (candidateList.isNotEmpty()) {
                item {
                    Text(
                        text = "Select Video or High-Res Photo to Shrink",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(candidateList, key = { it.mediaStoreId }) { media ->
                            val isSelected = media.mediaStoreId == activeItem?.mediaStoreId
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) CompressionCyan else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .width(150.dp)
                                    .clickable { onSelectMediaItem(media) }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(78.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        AsyncImage(
                                            model = Uri.parse(media.contentUri),
                                            contentDescription = media.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.72f),
                                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Icon(
                                                imageVector = if (media.mediaType == MediaType.VIDEO) Icons.Default.Movie else Icons.Default.Photo,
                                                contentDescription = null,
                                                tint = CompressionCyan,
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = CleanVaultViewModel.formatStorageSize(media.sizeBytes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CompressionCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = media.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (activeItem == null || compressionPlan == null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No media selected. Seed demo media or scan your gallery from the Dashboard first.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                return@LazyColumn
            }

            // Live MB Estimation Hero Card
            item {
                val shrinkByPercent = (100 - compressionPlan.targetPercentage).coerceIn(5, 85)
                ElevatedCard(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = CompressionCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "SHRINK BY $shrinkByPercent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CompressionCyan,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "Est. Save ${CleanVaultViewModel.formatStorageSize(compressionPlan.estimatedSavingsBytes)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = KeepMintGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "ORIGINAL SIZE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CleanVaultViewModel.formatStorageSize(compressionPlan.originalBytes),
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.displayMedium,
                                color = CompressionCyan
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "PREDICTED OUTPUT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CompressionCyan
                                )
                                Text(
                                    text = "~${CleanVaultViewModel.formatStorageSize(compressionPlan.targetBytes)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = CompressionCyan
                                )
                            }
                        }

                        // Interactive Compression Percentage Slider (20% to 90% of original size)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Target File Size Slider (${(studio.targetRatioSlider * 100).roundToInt()}% of original)",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "20% .. 90%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Slider(
                                value = studio.targetRatioSlider,
                                onValueChange = onSliderRatioChanged,
                                valueRange = 0.20f..0.90f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CompressionCyan,
                                    activeTrackColor = CompressionCyan
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("compression_percentage_slider")
                            )
                        }

                        // Reverse Bitrate & Resolution Telemetry Box
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (activeItem.mediaType == MediaType.VIDEO) {
                                val kbps = compressionPlan.targetVideoBitrateBps / 1000
                                Text(
                                    text = "Media3 Bitrate Formula: Video ${kbps} kbps + Audio 128 kbps • Scaling: ${compressionPlan.targetHeightPx}p • Hardware H.265/HEVC (H.264 fallback)",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            } else {
                                Text(
                                    text = "Photo Downscaler: Quality ${compressionPlan.photoQuality}% • EXIF Tags Preserved (DateTimeOriginal, GPS Lat/Long, Make/Model)",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Smart Panda-Style Presets
            item {
                Text(
                    text = "Smart Panda-Style Presets",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompressionPreset.entries.forEach { preset ->
                        val isSelected = studio.selectedPreset == preset
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) CompressionCyan else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPreset(preset) }
                                .testTag("preset_${preset.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = CompressionCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Custom Target MB Input + Photo WebP Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = studio.customTargetMbInput,
                        onValueChange = onCustomTargetMbChanged,
                        label = { Text("Exact Target MB (Optional)") },
                        placeholder = { Text("e.g. 15.0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_target_mb_input")
                    )

                    if (activeItem.mediaType == MediaType.PHOTO) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("WebP Lossy", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = studio.useWebpForPhotos,
                                onCheckedChange = onToggleWebpPhoto
                            )
                        }
                    }
                }
            }

            // Execute Compression CTA
            item {
                Button(
                    onClick = onStartCompression,
                    enabled = !studio.isCompressing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CompressionCyan,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("execute_compression_button")
                ) {
                    if (studio.isCompressing) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Encoding Hardware Stream (${studio.progressPercent}%)...",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(Icons.Default.Compress, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compress Now → Target ~${CleanVaultViewModel.formatStorageSize(compressionPlan.targetBytes)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Safe Post-Compression Before/After Card
            studio.lastResult?.let { result ->
                item {
                    PostCompressionResultCard(
                        result = result,
                        onReplaceOriginal = { origId -> onReplaceOriginalToTrash(origId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PostCompressionResultCard(
    result: CompressionExecutionResult,
    onReplaceOriginal: (Long) -> Unit
) {
    when (result) {
        is CompressionExecutionResult.Success -> {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OcrEmeraldShield.copy(alpha = 0.14f)
                ),
                border = BorderStroke(1.5.dp, OcrEmeraldShield),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = "Success",
                            tint = OcrEmeraldShield
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compression Complete • ${result.codecUsed}",
                            style = MaterialTheme.typography.titleMedium,
                            color = OcrEmeraldShield,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Before: ${CleanVaultViewModel.formatStorageSize(result.originalSizeBytes)}  →  After: ${CleanVaultViewModel.formatStorageSize(result.compressedSizeBytes)} (Saved ${CleanVaultViewModel.formatStorageSize(result.savedBytes)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Saved as: ${result.outputDisplayName} (${result.outputWidth}x${result.outputHeight})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onReplaceOriginal(result.originalMedia.mediaStoreId) },
                            colors = ButtonDefaults.buttonColors(containerColor = TrashCoralRed),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("replace_original_trash_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Replace Original (Stage Trash)")
                        }
                    }
                }
            }
        }

        is CompressionExecutionResult.Failure -> {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TrashCoralRed.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = result.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TrashCoralRed,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
