package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ScanMode
import com.example.data.local.SensitivityLevel
import com.example.data.local.SmartCategory
import com.example.domain.scanner.BatteryOptimizationHelper
import com.example.ui.theme.CompressionCyan
import com.example.ui.theme.CrownAmberGold
import com.example.ui.theme.KeepMintGreen
import com.example.ui.theme.OcrEmeraldShield
import com.example.ui.theme.TrashCoralRed
import com.example.ui.viewmodel.CleanVaultUiState
import com.example.ui.viewmodel.CleanVaultViewModel
import com.example.ui.viewmodel.VaultDestination

@Composable
fun OnboardingScanSelectorScreen(
    uiState: CleanVaultUiState,
    onRequestPermissionsAndStartScan: (ScanMode) -> Unit,
    onSeedSampleGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box(modifier = Modifier.height(210.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.img_onboarding_hero),
                            contentDescription = "CleanVault On-Device Media Intelligence",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.82f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp)
                        ) {
                            Surface(
                                color = OcrEmeraldShield.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, OcrEmeraldShield)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "100% Offline Privacy",
                                        tint = OcrEmeraldShield,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "100% ON-DEVICE • ZERO CLOUD UPLOADS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.onboarding_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text(
                    text = "Select Initial Scan Mode",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Mode A: Quick Batch (Instant Start - 500 items)
            item {
                ScanModeOptionCard(
                    title = stringResource(R.string.quick_batch_title),
                    badge = "RECOMMENDED • < 2 SEC",
                    description = stringResource(R.string.quick_batch_desc),
                    icon = Icons.Default.Bolt,
                    accentColor = CompressionCyan,
                    testTag = "scan_mode_quick_batch_card",
                    onClick = { onRequestPermissionsAndStartScan(ScanMode.QUICK_BATCH) }
                )
            }

            // Mode B: Full Deep Scan Now
            item {
                ScanModeOptionCard(
                    title = stringResource(R.string.full_scan_title),
                    badge = "FOREGROUND SERVICE PROTECTED",
                    description = stringResource(R.string.full_scan_desc),
                    icon = Icons.Default.Radar,
                    accentColor = CrownAmberGold,
                    testTag = "scan_mode_full_deep_card",
                    onClick = { onRequestPermissionsAndStartScan(ScanMode.FULL_DEEP_SCAN) }
                )
            }

            // Sample Media Seeder for Empty Emulators
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Demo Gallery Seeder",
                                tint = CrownAmberGold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Testing on an Empty Emulator?",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Generate real burst clusters (sharp vs. blurry), an OCR tax receipt, a screenshot, and an MP4 video directly in MediaStore.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onSeedSampleGallery,
                            enabled = !uiState.isSeedingDemoMedia,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("seed_sample_media_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            if (uiState.isSeedingDemoMedia) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Generating Burst Photos, OCR Receipt & MP4 Video...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.seed_sample_media))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanModeOptionCard(
    title: String,
    badge: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.16f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = accentColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Start",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SmartTriageDashboardScreen(
    uiState: CleanVaultUiState,
    onStartScan: (ScanMode) -> Unit,
    onPauseResumeScan: () -> Unit,
    onCancelScan: () -> Unit,
    onSeedSampleMedia: () -> Unit,
    onSensitivityChanged: (SensitivityLevel) -> Unit,
    onOpenCategory: (SmartCategory) -> Unit,
    onOpenCluster: (String) -> Unit,
    onBatchSmartCleanAllClusters: () -> Unit,
    onNavigateToDestination: (VaultDestination) -> Unit,
    onToggleProPreview: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Storage Reclaim Hero Card
            item {
                StorageIntelligenceHeroCard(
                    uiState = uiState,
                    onQuickScanClick = { onStartScan(ScanMode.QUICK_BATCH) },
                    onDeepScanClick = { onStartScan(ScanMode.FULL_DEEP_SCAN) },
                    onSeedDemoClick = onSeedSampleMedia,
                    onOpenTrashBin = { onNavigateToDestination(VaultDestination.STAGING_TRASH_BIN) }
                )
            }

            // 2. Live Foreground Service Scan Progress Banner (if scanning)
            item {
                AnimatedVisibility(visible = uiState.scanTelemetry.isScanning) {
                    LiveScanProgressCard(
                        telemetry = uiState.scanTelemetry,
                        onPauseResume = onPauseResumeScan,
                        onCancel = onCancelScan
                    )
                }
            }

            // 3. OEM Battery / RAM Manager Kill-Protection Rationale Banner
            if (!uiState.isIgnoringBatteryOpt) {
                item {
                    OemBatteryGuardBanner(context = context)
                }
            }

            // 4. Quick Gamified Action Row (Tinder Photo Swipe, TikTok Video Feed, Panda Compressor)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickLaunchTile(
                        title = "Swipe Deck",
                        subtitle = "${uiState.swipePhotoDeck.size} photos",
                        icon = Icons.Default.Swipe,
                        accent = CrownAmberGold,
                        testTag = "launch_swipe_deck_tile",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToDestination(VaultDestination.SWIPE_DECK) }
                    )
                    QuickLaunchTile(
                        title = "Video Triage",
                        subtitle = "${uiState.videoTriageFeed.size} clips",
                        icon = Icons.Default.VideoLibrary,
                        accent = CompressionCyan,
                        testTag = "launch_video_triage_tile",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToDestination(VaultDestination.VIDEO_TRIAGE) }
                    )
                    QuickLaunchTile(
                        title = "Downscaler",
                        subtitle = "Panda H.265",
                        icon = Icons.Default.Compress,
                        accent = OcrEmeraldShield,
                        testTag = "launch_compressor_tile",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToDestination(VaultDestination.COMPRESSOR_STUDIO) }
                    )
                }
            }

            // 5. Perceptual dHash Sensitivity Control (Strict <= 4, Balanced <= 8, Loose <= 12)
            item {
                SensitivityControlCard(
                    currentLevel = uiState.entitlementState.sensitivityLevel,
                    clusterCount = uiState.similarClusters.size,
                    onSelectLevel = onSensitivityChanged,
                    onBatchCleanAll = onBatchSmartCleanAllClusters
                )
            }

            // 6. Smart Triage Categories ("Biggest Wins First")
            item {
                Text(
                    text = "Smart Triage Categories (Biggest Wins First)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                val clusterBytes = uiState.similarClusters.sumOf { it.reclaimableBytes }
                SmartCategoryRowCard(
                    category = SmartCategory.SIMILAR_CLUSTERS,
                    countLabel = "${uiState.similarClusters.size} clusters",
                    sizeLabel = CleanVaultViewModel.formatStorageSize(clusterBytes),
                    badgeText = "BEST SHOT CROWNED",
                    accentColor = CrownAmberGold,
                    icon = Icons.Default.FilterNone,
                    testTag = "category_similar_clusters",
                    onClick = { onOpenCategory(SmartCategory.SIMILAR_CLUSTERS) }
                )
            }

            item {
                val videoBytes = uiState.videoTriageFeed.sumOf { it.sizeBytes }
                SmartCategoryRowCard(
                    category = SmartCategory.HEAVY_VIDEOS,
                    countLabel = "${uiState.videoTriageFeed.size} videos",
                    sizeLabel = CleanVaultViewModel.formatStorageSize(videoBytes),
                    badgeText = "MEDIA3 H.265",
                    accentColor = CompressionCyan,
                    icon = Icons.Default.VideoLibrary,
                    testTag = "category_heavy_videos",
                    onClick = { onOpenCategory(SmartCategory.HEAVY_VIDEOS) }
                )
            }

            item {
                val docBytes = uiState.protectedDocuments.sumOf { it.sizeBytes }
                SmartCategoryRowCard(
                    category = SmartCategory.OCR_DOCUMENTS,
                    countLabel = "${uiState.protectedDocuments.size} protected",
                    sizeLabel = CleanVaultViewModel.formatStorageSize(docBytes),
                    badgeText = "OCR GUARDRAIL SHIELD",
                    accentColor = OcrEmeraldShield,
                    icon = Icons.Default.Description,
                    testTag = "category_ocr_documents",
                    onClick = { onOpenCategory(SmartCategory.OCR_DOCUMENTS) }
                )
            }

            item {
                val screenBytes = uiState.screenshotsAndSocial.sumOf { it.sizeBytes }
                SmartCategoryRowCard(
                    category = SmartCategory.SCREENSHOTS_SOCIAL,
                    countLabel = "${uiState.screenshotsAndSocial.size} items",
                    sizeLabel = CleanVaultViewModel.formatStorageSize(screenBytes),
                    badgeText = "CLUTTER",
                    accentColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.ScreenshotMonitor,
                    testTag = "category_screenshots_social",
                    onClick = { onOpenCategory(SmartCategory.SCREENSHOTS_SOCIAL) }
                )
            }

            item {
                val blurryBytes = uiState.blurryAndBadShots.sumOf { it.sizeBytes }
                SmartCategoryRowCard(
                    category = SmartCategory.BLURRY_EXPOSURE,
                    countLabel = "${uiState.blurryAndBadShots.size} shots",
                    sizeLabel = CleanVaultViewModel.formatStorageSize(blurryBytes),
                    badgeText = "LAPLACIAN + LUMA",
                    accentColor = TrashCoralRed,
                    icon = Icons.Default.BlurOn,
                    testTag = "category_blurry_exposure",
                    onClick = { onOpenCategory(SmartCategory.BLURRY_EXPOSURE) }
                )
            }

            // 7. Monthly / "On This Day" Micro-Chapters
            if (uiState.microChaptersMap.isNotEmpty()) {
                item {
                    Text(
                        text = "Chronological Micro-Chapters",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(uiState.microChaptersMap.entries.toList(), key = { it.key }) { (monthKey, list) ->
                            val monthBytes = list.sumOf { it.sizeBytes }
                            ElevatedCard(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .width(190.dp)
                                    .clickable { onOpenCategory(SmartCategory.MICRO_CHAPTERS) }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = monthKey,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${list.size} items • ${CleanVaultViewModel.formatStorageSize(monthBytes)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 8. Commercialization Hook Status (v1 Free Unlocked, v2 Google Play Billing Ready)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                                text = "EntitlementGate • v1 Free Edition (Pro Unlocked)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Toggle off to preview v2 Google Play Billing feature gates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.entitlementState.isProUnlocked,
                            onCheckedChange = onToggleProPreview,
                            modifier = Modifier.testTag("entitlement_pro_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageIntelligenceHeroCard(
    uiState: CleanVaultUiState,
    onQuickScanClick: () -> Unit,
    onDeepScanClick: () -> Unit,
    onSeedDemoClick: () -> Unit,
    onOpenTrashBin: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radial Storage Ring
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val total = maxOf(1L, uiState.totalLibraryBytes)
                    val reclaimRatio = (uiState.totalReclaimableBytes.toFloat() / total.toFloat())
                        .coerceIn(0.08f, 0.92f)
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = CompressionCyan,
                            startAngle = -90f,
                            sweepAngle = 360f * reclaimRatio,
                            useCenter = false,
                            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(reclaimRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Savable",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Potential Space to Reclaim",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CleanVaultViewModel.formatStorageSize(uiState.totalReclaimableBytes),
                        style = MaterialTheme.typography.headlineLarge,
                        color = CompressionCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.allMedia.size} indexed • ${CleanVaultViewModel.formatStorageSize(uiState.totalLibraryBytes)} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.scanTelemetry.lifetimeBytesReclaimed > 0L) {
                        Text(
                            text = "Lifetime saved: ${CleanVaultViewModel.formatStorageSize(uiState.scanTelemetry.lifetimeBytesReclaimed)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = KeepMintGreen
                        )
                    }
                }
            }

            if (uiState.stagedTrashItems.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TrashCoralRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, TrashCoralRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenTrashBin)
                        .testTag("hero_staging_trash_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Staging Bin",
                                tint = TrashCoralRed
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Staging Bin: ${uiState.stagedTrashItems.size} items (${CleanVaultViewModel.formatStorageSize(uiState.stagedTrashBytes)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = TrashCoralRed
                            )
                        }
                        Text(
                            text = "Review & Empty →",
                            style = MaterialTheme.typography.labelMedium,
                            color = TrashCoralRed
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onQuickScanClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_quick_scan_button")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Quick Scan", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDeepScanClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_deep_scan_button")
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Deep Scan", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onSeedDemoClick,
                    enabled = !uiState.isSeedingDemoMedia,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("dashboard_seed_demo_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Seed Demo Media", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveScanProgressCard(
    telemetry: com.example.data.local.ScanTelemetryEntity,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (telemetry.isPaused) "Gallery Scan Paused" else "Foreground Media Processing Active",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = telemetry.statusMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
                Row {
                    IconButton(
                        onClick = onPauseResume,
                        modifier = Modifier.testTag("scan_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (telemetry.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (telemetry.isPaused) "Resume Scan" else "Pause Scan",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("scan_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Scan",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            val progress = if (telemetry.totalLibraryCount > 0) {
                (telemetry.scannedCount.toFloat() / telemetry.totalLibraryCount.toFloat()).coerceIn(0f, 1f)
            } else 0.2f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun OemBatteryGuardBanner(context: Context) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CrownAmberGold.copy(alpha = 0.14f)
        ),
        border = BorderStroke(1.dp, CrownAmberGold.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = "Battery Protection",
                tint = CrownAmberGold,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OEM RAM Kill Protection",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Allow unrestricted background media processing so Samsung/Xiaomi/OnePlus RAM managers don't interrupt 20,000+ item scans.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            BatteryOptimizationHelper.createIgnoreBatteryOptimizationsIntent(context)
                        )
                    }
                },
                modifier = Modifier.testTag("battery_opt_button")
            ) {
                Text("Allow")
            }
        }
    }
}

@Composable
private fun QuickLaunchTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.18f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SensitivityControlCard(
    currentLevel: SensitivityLevel,
    clusterCount: Int,
    onSelectLevel: (SensitivityLevel) -> Unit,
    onBatchCleanAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "dHash Sensitivity",
                        tint = CrownAmberGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "64-Bit dHash Clustering Sensitivity",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = "$clusterCount clusters",
                    style = MaterialTheme.typography.labelMedium,
                    color = CrownAmberGold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SensitivityLevel.entries.forEach { level ->
                    val selected = level == currentLevel
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectLevel(level) },
                        label = {
                            Text(
                                text = level.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CrownAmberGold.copy(alpha = 0.22f),
                            selectedLabelColor = CrownAmberGold
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sensitivity_chip_${level.name.lowercase()}")
                    )
                }
            }

            if (clusterCount > 0) {
                Button(
                    onClick = onBatchCleanAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrownAmberGold,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("batch_keep_best_all_clusters_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Keep Crowned Best Shots & Stage All Duplicates",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartCategoryRowCard(
    category: SmartCategory,
    countLabel: String,
    sizeLabel: String,
    badgeText: String,
    accentColor: Color,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.16f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = category.title,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = countLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
