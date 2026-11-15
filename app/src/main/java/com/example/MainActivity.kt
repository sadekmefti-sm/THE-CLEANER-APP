package com.example

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.ScanMode
import com.example.ui.screens.CategoryMediaListScreen
import com.example.ui.screens.OnboardingScanSelectorScreen
import com.example.ui.screens.PandaCompressorStudioScreen
import com.example.ui.screens.SimilarClusterReviewScreen
import com.example.ui.screens.SmartTriageDashboardScreen
import com.example.ui.screens.StagingTrashBinScreen
import com.example.ui.screens.SynchronizedZoomInspectorScreen
import com.example.ui.screens.TikTokVideoTriageScreen
import com.example.ui.screens.TinderPhotoSwipeDeckScreen
import com.example.ui.theme.CleanVaultTheme
import com.example.ui.viewmodel.CleanVaultViewModel
import com.example.ui.viewmodel.VaultDestination

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CleanVaultTheme {
                CleanVaultAppRoot()
            }
        }
    }
}

@Composable
fun CleanVaultAppRoot(
    viewModel: CleanVaultViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingTrashIntentSender by viewModel.pendingTrashIntentSender.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingScanMode by remember { mutableStateOf<ScanMode?>(null) }

    // Runtime Permission Launcher for Android 11–15+ Media & Notification Permissions
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val mode = pendingScanMode ?: ScanMode.QUICK_BATCH
        viewModel.startGalleryScan(mode)
        pendingScanMode = null
    }

    // Scoped Storage MediaStore.createTrashRequest IntentSender Launcher (sequentially launches 1,000-item batches)
    val trashRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onSystemTrashBatchResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(pendingTrashIntentSender) {
        val sender = pendingTrashIntentSender
        if (sender != null) {
            trashRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    fun requestMediaPermissionsAndScan(mode: ScanMode) {
        pendingScanMode = mode
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }.toTypedArray()
        permissionsLauncher.launch(perms)
    }

    // v2 Pro Entitlement Gate Preview Dialog (when user toggles off v1 Free switch in Dashboard)
    uiState.paywallPreviewFeature?.let { feature ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPaywallPreview() },
            title = { Text("CleanVault Pro Gate Preview") },
            text = {
                Text(
                    "${feature.title}: ${feature.description}\n\n" +
                        "In v1 Free Edition, all features are unlocked by default. Unlock now to continue."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleProGateForTesting(true)
                        viewModel.dismissPaywallPreview()
                    }
                ) {
                    Text("Unlock Free (v1 Default)")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPaywallPreview() }) {
                    Text("Dismiss")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (uiState.hasCompletedOnboarding) {
                NavigationBar {
                    NavigationBarItem(
                        selected = uiState.currentDestination == VaultDestination.DASHBOARD ||
                            uiState.currentDestination == VaultDestination.CLUSTER_DETAIL ||
                            uiState.currentDestination == VaultDestination.CATEGORY_LIST,
                        onClick = { viewModel.navigateTo(VaultDestination.DASHBOARD) },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Triage") },
                        modifier = Modifier.testTag("nav_tab_dashboard")
                    )
                    NavigationBarItem(
                        selected = uiState.currentDestination == VaultDestination.SWIPE_DECK,
                        onClick = { viewModel.navigateTo(VaultDestination.SWIPE_DECK) },
                        icon = { Icon(Icons.Default.Swipe, contentDescription = "Swipe Deck") },
                        label = { Text("Swipe") },
                        modifier = Modifier.testTag("nav_tab_swipe")
                    )
                    NavigationBarItem(
                        selected = uiState.currentDestination == VaultDestination.VIDEO_TRIAGE,
                        onClick = { viewModel.navigateTo(VaultDestination.VIDEO_TRIAGE) },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Video Triage") },
                        label = { Text("Videos") },
                        modifier = Modifier.testTag("nav_tab_videos")
                    )
                    NavigationBarItem(
                        selected = uiState.currentDestination == VaultDestination.COMPRESSOR_STUDIO,
                        onClick = { viewModel.navigateTo(VaultDestination.COMPRESSOR_STUDIO) },
                        icon = { Icon(Icons.Default.Compress, contentDescription = "Downscaler") },
                        label = { Text("Shrink") },
                        modifier = Modifier.testTag("nav_tab_compressor")
                    )
                    NavigationBarItem(
                        selected = uiState.currentDestination == VaultDestination.STAGING_TRASH_BIN,
                        onClick = { viewModel.navigateTo(VaultDestination.STAGING_TRASH_BIN) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.stagedTrashItems.isNotEmpty()) {
                                        Badge { Text("${uiState.stagedTrashItems.size}") }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Trash Bin")
                            }
                        },
                        label = { Text("Trash") },
                        modifier = Modifier.testTag("nav_tab_trash")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.hasCompletedOnboarding) {
                OnboardingScanSelectorScreen(
                    uiState = uiState,
                    onRequestPermissionsAndStartScan = { mode -> requestMediaPermissionsAndScan(mode) },
                    onSeedSampleGallery = { viewModel.seedDemoMediaAndScan() }
                )
            } else {
                when (uiState.currentDestination) {
                    VaultDestination.DASHBOARD -> {
                        SmartTriageDashboardScreen(
                            uiState = uiState,
                            onStartScan = { mode -> requestMediaPermissionsAndScan(mode) },
                            onPauseResumeScan = { viewModel.pauseOrResumeActiveScan() },
                            onCancelScan = { viewModel.cancelActiveScan() },
                            onSeedSampleMedia = { viewModel.seedDemoMediaAndScan() },
                            onSensitivityChanged = { level -> viewModel.updateSensitivityLevel(level) },
                            onOpenCategory = { cat -> viewModel.openSmartCategory(cat) },
                            onOpenCluster = { id -> viewModel.openClusterDetail(id) },
                            onBatchSmartCleanAllClusters = { viewModel.keepBestAndStageRestAllClusters() },
                            onNavigateToDestination = { dest -> viewModel.navigateTo(dest) },
                            onToggleProPreview = { unlocked -> viewModel.toggleProGateForTesting(unlocked) }
                        )
                    }

                    VaultDestination.CLUSTER_DETAIL -> {
                        SimilarClusterReviewScreen(
                            uiState = uiState,
                            onSelectCluster = { id -> viewModel.openClusterDetail(id) },
                            onCrownManualBestShot = { clusterId, mediaId ->
                                viewModel.setManualBestShotCrown(clusterId, mediaId)
                            },
                            onKeepBestAndStageRest = { clusterId ->
                                viewModel.keepBestAndStageRestInCluster(clusterId)
                            },
                            onOpenSyncInspector = { primary, second ->
                                viewModel.openSyncZoomInspector(primary, second)
                            },
                            onStageSingleItem = { item ->
                                viewModel.triageSingleItem(item, com.example.data.local.TriageStatus.STAGED_FOR_TRASH)
                            },
                            onBack = { viewModel.navigateTo(VaultDestination.DASHBOARD) }
                        )
                    }

                    VaultDestination.SYNC_ZOOM_INSPECTOR -> {
                        val pair = uiState.inspectPair
                        if (pair != null) {
                            SynchronizedZoomInspectorScreen(
                                primary = pair.first,
                                comparison = pair.second,
                                onCrownBestShot = { chosen ->
                                    chosen.clusterId?.let { cid ->
                                        viewModel.setManualBestShotCrown(cid, chosen.mediaStoreId)
                                    }
                                },
                                onBack = { viewModel.navigateTo(VaultDestination.CLUSTER_DETAIL) }
                            )
                        } else {
                            viewModel.navigateTo(VaultDestination.CLUSTER_DETAIL)
                        }
                    }

                    VaultDestination.CATEGORY_LIST -> {
                        CategoryMediaListScreen(
                            uiState = uiState,
                            onStageAllForTrash = { items -> viewModel.stageAllCategoryItemsForTrash(items) },
                            onStageSingleItem = { item ->
                                viewModel.triageSingleItem(item, com.example.data.local.TriageStatus.STAGED_FOR_TRASH)
                            },
                            onOpenCompressor = { item -> viewModel.openCompressorForItem(item) },
                            onBack = { viewModel.navigateTo(VaultDestination.DASHBOARD) }
                        )
                    }

                    VaultDestination.COMPRESSOR_STUDIO -> {
                        PandaCompressorStudioScreen(
                            uiState = uiState,
                            compressionPlan = viewModel.computeCurrentCompressionPlan(),
                            onSelectMediaItem = { item -> viewModel.openCompressorForItem(item) },
                            onSelectPreset = { preset -> viewModel.selectCompressionPreset(preset) },
                            onSliderRatioChanged = { ratio -> viewModel.updateCompressionRatioSlider(ratio) },
                            onCustomTargetMbChanged = { input -> viewModel.updateCustomTargetMbInput(input) },
                            onToggleWebpPhoto = { enabled -> viewModel.toggleUseWebpForPhotos(enabled) },
                            onStartCompression = { viewModel.executeCompression() },
                            onReplaceOriginalToTrash = { origId -> viewModel.replaceOriginalAfterCompression(origId) },
                            onBack = { viewModel.navigateTo(VaultDestination.DASHBOARD) }
                        )
                    }

                    VaultDestination.SWIPE_DECK -> {
                        TinderPhotoSwipeDeckScreen(
                            uiState = uiState,
                            onTriagePhoto = { item, status -> viewModel.triageSingleItem(item, status) },
                            onUndoLastSwipe = { viewModel.undoLastTriage() },
                            onBack = { viewModel.navigateTo(VaultDestination.DASHBOARD) }
                        )
                    }

                    VaultDestination.VIDEO_TRIAGE -> {
                        TikTokVideoTriageScreen(
                            uiState = uiState,
                            onOpenCompressorForVideo = { video -> viewModel.openCompressorForItem(video) },
                            onTriageVideo = { video, status -> viewModel.triageSingleItem(video, status) },
                            onBack = { viewModel.navigateTo(VaultDestination.DASHBOARD) }
                        )
                    }

                    VaultDestination.STAGING_TRASH_BIN -> {
                        StagingTrashBinScreen(
                            uiState = uiState,
                            onRestoreSingleItem = { id -> viewModel.restoreFromStagingBin(id) },
                            onRestoreAllItems = { viewModel.restoreAllFromStagingBin() },
                            onEmptyStagingBin = { viewModel.requestEmptyStagingBin() },
                            onBack = { viewModel.navigateTo(VaultDestination.DASHBOARD) }
                        )
                    }
                }
            }
        }
    }
}
