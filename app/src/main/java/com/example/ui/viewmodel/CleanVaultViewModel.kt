package com.example.ui.viewmodel

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CleanVaultDatabase
import com.example.data.local.MediaItemEntity
import com.example.data.local.ScanMode
import com.example.data.local.ScanTelemetryEntity
import com.example.data.local.SensitivityLevel
import com.example.data.local.SimilarClusterEntity
import com.example.data.local.SmartCategory
import com.example.data.local.TriageStatus
import com.example.domain.billing.EntitlementState
import com.example.domain.billing.LocalEntitlementManager
import com.example.domain.billing.PremiumFeature
import com.example.domain.clustering.SimilarityClusterEngine
import com.example.domain.compression.CompressionExecutionResult
import com.example.domain.compression.CompressionPlan
import com.example.domain.compression.CompressionPreset
import com.example.domain.compression.PandaMediaCompressorEngine
import com.example.domain.scanner.BatteryOptimizationHelper
import com.example.domain.scanner.SampleMediaSeeder
import com.example.domain.storage.ScopedStorageTrashManager
import com.example.worker.GalleryScanWorker
import com.example.worker.ScanControlBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class VaultDestination {
    DASHBOARD,
    SWIPE_DECK,
    VIDEO_TRIAGE,
    COMPRESSOR_STUDIO,
    STAGING_TRASH_BIN,
    CLUSTER_DETAIL,
    CATEGORY_LIST,
    SYNC_ZOOM_INSPECTOR
}

data class UndoTriageRecord(
    val mediaStoreId: Long,
    val previousStatus: TriageStatus,
    val displayName: String
)

@Immutable
data class CompressionStudioUiState(
    val selectedItem: MediaItemEntity? = null,
    val selectedPreset: CompressionPreset = CompressionPreset.BALANCED,
    val targetRatioSlider: Float = 0.45f, // 45% of original size (shrink by 55%)
    val customTargetMbInput: String = "",
    val useWebpForPhotos: Boolean = false,
    val isCompressing: Boolean = false,
    val progressPercent: Int = 0,
    val lastResult: CompressionExecutionResult? = null
)

@Immutable
data class CleanVaultUiState(
    val currentDestination: VaultDestination = VaultDestination.DASHBOARD,
    val selectedCategory: SmartCategory = SmartCategory.SIMILAR_CLUSTERS,
    val selectedClusterId: String? = null,
    val inspectPair: Pair<MediaItemEntity, MediaItemEntity>? = null,
    val hasCompletedOnboarding: Boolean = false,
    val isSeedingDemoMedia: Boolean = false,
    val isIgnoringBatteryOpt: Boolean = true,
    val allMedia: List<MediaItemEntity> = emptyList(),
    val swipePhotoDeck: List<MediaItemEntity> = emptyList(),
    val videoTriageFeed: List<MediaItemEntity> = emptyList(),
    val stagedTrashItems: List<MediaItemEntity> = emptyList(),
    val protectedDocuments: List<MediaItemEntity> = emptyList(),
    val screenshotsAndSocial: List<MediaItemEntity> = emptyList(),
    val blurryAndBadShots: List<MediaItemEntity> = emptyList(),
    val similarClusters: List<SimilarClusterEntity> = emptyList(),
    val clusterMembersMap: Map<String, List<MediaItemEntity>> = emptyMap(),
    val microChaptersMap: Map<String, List<MediaItemEntity>> = emptyMap(),
    val scanTelemetry: ScanTelemetryEntity = ScanTelemetryEntity(),
    val entitlementState: EntitlementState = EntitlementState(),
    val compressionStudio: CompressionStudioUiState = CompressionStudioUiState(),
    val canUndoSwipe: Boolean = false,
    val snackbarMessage: String? = null,
    val paywallPreviewFeature: PremiumFeature? = null
) {
    val totalLibraryBytes: Long
        get() = allMedia.sumOf { it.sizeBytes }

    val stagedTrashBytes: Long
        get() = stagedTrashItems.sumOf { it.sizeBytes }

    val totalReclaimableBytes: Long
        get() {
            val clusterSavings = similarClusters.sumOf { it.reclaimableBytes }
            val blurryBytes = blurryAndBadShots.sumOf { it.sizeBytes }
            val screenshotBytes = screenshotsAndSocial.sumOf { it.sizeBytes }
            val videoSavings = (videoTriageFeed.sumOf { it.sizeBytes } * 0.55).toLong()
            return clusterSavings + blurryBytes + screenshotBytes + videoSavings
        }
}

class CleanVaultViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = CleanVaultDatabase.getInstance(appContext)
    private val dao = db.mediaDao()
    private val entitlementManager = LocalEntitlementManager(appContext)

    private val undoStack = ArrayDeque<UndoTriageRecord>()

    private val _navState = MutableStateFlow(VaultDestination.DASHBOARD)
    private val _selectedCategory = MutableStateFlow(SmartCategory.SIMILAR_CLUSTERS)
    private val _selectedClusterId = MutableStateFlow<String?>(null)
    private val _inspectPair = MutableStateFlow<Pair<MediaItemEntity, MediaItemEntity>?>(null)
    private val _hasCompletedOnboarding = MutableStateFlow(false)
    private val _isSeedingDemo = MutableStateFlow(false)
    private val _compressionStudio = MutableStateFlow(CompressionStudioUiState())
    private val _canUndo = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _paywallPreview = MutableStateFlow<PremiumFeature?>(null)

    val uiState: StateFlow<CleanVaultUiState> = combine(
        dao.observeAllMedia(),
        dao.observeSwipePhotoDeck(),
        dao.observeVideoTriageFeed(),
        dao.observeStagedTrashItems(),
        dao.observeProtectedDocuments(),
        dao.observeScreenshotsAndSocial(),
        dao.observeBlurryAndBadShots(),
        dao.observeSimilarClusters(),
        dao.observeAllClusteredItems(),
        dao.observeScanTelemetry(),
        entitlementManager.entitlementState,
        _navState,
        _selectedCategory,
        _selectedClusterId,
        _inspectPair,
        _hasCompletedOnboarding,
        _isSeedingDemo,
        _compressionStudio,
        _canUndo,
        _snackbarMessage,
        _paywallPreview
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val allMedia = args[0] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val swipeDeck = args[1] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val videoFeed = args[2] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val stagedTrash = args[3] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val protectedDocs = args[4] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val screenshots = args[5] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val blurryShots = args[6] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val clusters = args[7] as List<SimilarClusterEntity>
        @Suppress("UNCHECKED_CAST")
        val clusteredItems = args[8] as List<MediaItemEntity>
        val telemetry = (args[9] as? ScanTelemetryEntity) ?: ScanTelemetryEntity()
        val entitlement = args[10] as EntitlementState
        val destination = args[11] as VaultDestination
        val selectedCategory = args[12] as SmartCategory
        val selectedClusterId = args[13] as String?
        @Suppress("UNCHECKED_CAST")
        val inspectPair = args[14] as Pair<MediaItemEntity, MediaItemEntity>?
        val onboardingDone = args[15] as Boolean
        val isSeeding = args[16] as Boolean
        val studio = args[17] as CompressionStudioUiState
        val canUndo = args[18] as Boolean
        val snackbar = args[19] as String?
        val paywall = args[20] as PremiumFeature?

        val clusterMembersMap = clusteredItems
            .filter { it.clusterId != null }
            .groupBy { it.clusterId!! }

        val microChapters = allMedia
            .filter { it.triageStatus != TriageStatus.STAGED_FOR_TRASH }
            .groupBy { it.monthYearKey.ifEmpty { "Recent Gallery" } }

        val activeStudioItem = studio.selectedItem
            ?: videoFeed.firstOrNull()
            ?: allMedia.firstOrNull()

        CleanVaultUiState(
            currentDestination = destination,
            selectedCategory = selectedCategory,
            selectedClusterId = selectedClusterId ?: clusters.firstOrNull()?.clusterId,
            inspectPair = inspectPair,
            hasCompletedOnboarding = onboardingDone || allMedia.isNotEmpty() || telemetry.isScanning,
            isSeedingDemoMedia = isSeeding,
            isIgnoringBatteryOpt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(appContext),
            allMedia = allMedia,
            swipePhotoDeck = swipeDeck,
            videoTriageFeed = videoFeed,
            stagedTrashItems = stagedTrash,
            protectedDocuments = protectedDocs,
            screenshotsAndSocial = screenshots,
            blurryAndBadShots = blurryShots,
            similarClusters = clusters,
            clusterMembersMap = clusterMembersMap,
            microChaptersMap = microChapters,
            scanTelemetry = telemetry,
            entitlementState = entitlement,
            compressionStudio = studio.copy(selectedItem = activeStudioItem),
            canUndoSwipe = canUndo,
            snackbarMessage = snackbar,
            paywallPreviewFeature = paywall
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CleanVaultUiState()
    )

    fun navigateTo(destination: VaultDestination) {
        _navState.value = destination
    }

    fun openSmartCategory(category: SmartCategory) {
        _selectedCategory.value = category
        when (category) {
            SmartCategory.SIMILAR_CLUSTERS -> {
                val firstCluster = uiState.value.similarClusters.firstOrNull()
                if (firstCluster != null) {
                    _selectedClusterId.value = firstCluster.clusterId
                    _navState.value = VaultDestination.CLUSTER_DETAIL
                } else {
                    _navState.value = VaultDestination.CATEGORY_LIST
                }
            }
            SmartCategory.HEAVY_VIDEOS -> {
                _navState.value = VaultDestination.VIDEO_TRIAGE
            }
            else -> {
                _navState.value = VaultDestination.CATEGORY_LIST
            }
        }
    }

    fun openClusterDetail(clusterId: String) {
        _selectedClusterId.value = clusterId
        _navState.value = VaultDestination.CLUSTER_DETAIL
    }

    fun openSyncZoomInspector(primary: MediaItemEntity, comparison: MediaItemEntity) {
        _inspectPair.value = primary to comparison
        _navState.value = VaultDestination.SYNC_ZOOM_INSPECTOR
    }

    fun openCompressorForItem(item: MediaItemEntity) {
        _compressionStudio.update {
            it.copy(
                selectedItem = item,
                lastResult = null,
                progressPercent = 0
            )
        }
        _navState.value = VaultDestination.COMPRESSOR_STUDIO
    }

    fun startGalleryScan(scanMode: ScanMode) {
        _hasCompletedOnboarding.value = true
        val sensitivity = uiState.value.entitlementState.sensitivityLevel
        GalleryScanWorker.enqueueScan(
            context = appContext,
            scanMode = scanMode,
            sensitivityLevel = sensitivity,
            autoQueueRemainder = (scanMode == ScanMode.QUICK_BATCH)
        )
    }

    fun pauseOrResumeActiveScan() {
        val currentlyPaused = ScanControlBus.isPaused.get()
        ScanControlBus.isPaused.set(!currentlyPaused)
        viewModelScope.launch {
            val current = dao.getScanTelemetry() ?: ScanTelemetryEntity()
            dao.upsertScanTelemetry(
                current.copy(
                    isPaused = !currentlyPaused,
                    statusMessage = if (!currentlyPaused) "Scan paused" else "Resuming scan..."
                )
            )
        }
    }

    fun cancelActiveScan() {
        ScanControlBus.isCancelled.set(true)
        ScanControlBus.isPaused.set(false)
        androidx.work.WorkManager.getInstance(appContext)
            .cancelUniqueWork(GalleryScanWorker.UNIQUE_WORK_NAME)
        viewModelScope.launch {
            val current = dao.getScanTelemetry() ?: ScanTelemetryEntity()
            dao.upsertScanTelemetry(
                current.copy(isScanning = false, isPaused = false, statusMessage = "Scan cancelled")
            )
        }
    }

    fun seedDemoMediaAndScan() {
        if (_isSeedingDemo.value) return
        viewModelScope.launch {
            _isSeedingDemo.value = true
            val created = withContext(Dispatchers.IO) {
                SampleMediaSeeder.seedDemoMediaToMediaStore(appContext)
            }
            _isSeedingDemo.value = false
            _hasCompletedOnboarding.value = true
            _snackbarMessage.value = "Seeded $created sample burst photos, OCR receipt & HD video!"
            startGalleryScan(ScanMode.QUICK_BATCH)
        }
    }

    fun updateSensitivityLevel(level: SensitivityLevel) {
        viewModelScope.launch {
            entitlementManager.setSensitivityLevel(level)
            withContext(Dispatchers.Default) {
                val analyzedPhotos = dao.getAnalyzedPhotosForClustering()
                if (analyzedPhotos.isNotEmpty()) {
                    val output = SimilarityClusterEngine.buildClusters(
                        contentResolver = appContext.contentResolver,
                        analyzedPhotos = analyzedPhotos,
                        sensitivity = level
                    )
                    dao.replaceAllClusters(output.clusters, output.updatedMediaItems)
                }
            }
            _snackbarMessage.value = "Re-clustered gallery with ${level.label} sensitivity"
        }
    }

    fun triageSingleItem(item: MediaItemEntity, targetStatus: TriageStatus) {
        viewModelScope.launch {
            undoStack.addLast(
                UndoTriageRecord(
                    mediaStoreId = item.mediaStoreId,
                    previousStatus = item.triageStatus,
                    displayName = item.displayName
                )
            )
            if (undoStack.size > 40) undoStack.removeFirst()
            _canUndo.value = undoStack.isNotEmpty()
            dao.updateTriageStatus(item.mediaStoreId, targetStatus)
        }
    }

    fun undoLastTriage() {
        val last = undoStack.removeLastOrNull() ?: return
        _canUndo.value = undoStack.isNotEmpty()
        viewModelScope.launch {
            dao.updateTriageStatus(last.mediaStoreId, last.previousStatus)
            _snackbarMessage.value = "Restored ${last.displayName}"
        }
    }

    fun setManualBestShotCrown(clusterId: String, newBestMediaId: Long) {
        viewModelScope.launch {
            dao.crownBestShotInCluster(clusterId, newBestMediaId)
            _snackbarMessage.value = "Updated Best Shot Crown for cluster"
        }
    }

    /**
     * Sticky "Keep Best & Stage Rest" action for a single cluster or across all clusters.
     */
    fun keepBestAndStageRestInCluster(clusterId: String) {
        viewModelScope.launch {
            if (!entitlementManager.isFeatureAllowed(PremiumFeature.ONE_TAP_SMART_CLEAN)) {
                _paywallPreview.value = PremiumFeature.ONE_TAP_SMART_CLEAN
                return@launch
            }
            val members = uiState.value.clusterMembersMap[clusterId].orEmpty()
            if (members.size < 2) return@launch

            val bestIds = members.filter { it.isBestInCluster }.map { it.mediaStoreId }
            val restIds = members.filter { !it.isBestInCluster && !it.isDocumentOrReceipt }.map { it.mediaStoreId }

            if (bestIds.isNotEmpty()) {
                dao.updateTriageStatusBatch(bestIds, TriageStatus.KEPT)
            }
            if (restIds.isNotEmpty()) {
                dao.updateTriageStatusBatch(restIds, TriageStatus.STAGED_FOR_TRASH)
            }
            val savedMb = members.filter { it.mediaStoreId in restIds }.sumOf { it.sizeBytes } / (1024 * 1024)
            _snackbarMessage.value = "Crowned Best Shot kept • ${restIds.size} duplicates (${savedMb} MB) staged for trash"
        }
    }

    fun keepBestAndStageRestAllClusters() {
        viewModelScope.launch {
            if (!entitlementManager.isFeatureAllowed(PremiumFeature.ONE_TAP_SMART_CLEAN)) {
                _paywallPreview.value = PremiumFeature.ONE_TAP_SMART_CLEAN
                return@launch
            }
            val allMembers = uiState.value.clusterMembersMap.values.flatten()
            val bestIds = allMembers.filter { it.isBestInCluster }.map { it.mediaStoreId }
            val restIds = allMembers.filter { !it.isBestInCluster && !it.isDocumentOrReceipt }.map { it.mediaStoreId }
            if (bestIds.isNotEmpty()) dao.updateTriageStatusBatch(bestIds, TriageStatus.KEPT)
            if (restIds.isNotEmpty()) dao.updateTriageStatusBatch(restIds, TriageStatus.STAGED_FOR_TRASH)
            _snackbarMessage.value = "Batch Smart Clean: ${restIds.size} duplicates staged into Trash Bin"
        }
    }

    fun stageAllCategoryItemsForTrash(items: List<MediaItemEntity>) {
        viewModelScope.launch {
            // Strictly respect OCR Document Guardrail
            val safeToTrashIds = items
                .filter { !it.isDocumentOrReceipt }
                .map { it.mediaStoreId }
            if (safeToTrashIds.isNotEmpty()) {
                dao.updateTriageStatusBatch(safeToTrashIds, TriageStatus.STAGED_FOR_TRASH)
                _snackbarMessage.value = "Staged ${safeToTrashIds.size} items to Trash Queue"
            }
        }
    }

    fun restoreFromStagingBin(mediaId: Long) {
        viewModelScope.launch {
            dao.updateTriageStatus(mediaId, TriageStatus.KEPT)
        }
    }

    fun restoreAllFromStagingBin() {
        val ids = uiState.value.stagedTrashItems.map { it.mediaStoreId }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            dao.updateTriageStatusBatch(ids, TriageStatus.UNREVIEWED)
            _snackbarMessage.value = "Restored ${ids.size} items from Staging Bin"
        }
    }

    /**
     * Invokes MediaStore.createTrashRequest(contentResolver, uris, true) for Android 11–15+
     * system 30-day Trash bin, or executes direct fallback delete if permitted.
     */
    fun buildEmptyStagingBinIntentSender(onDirectComplete: () -> Unit): IntentSender? {
        val staged = uiState.value.stagedTrashItems
        if (staged.isEmpty()) return null
        val uris = staged.map { Uri.parse(it.contentUri) }

        // First try direct deletion for app-created MediaStore entries (zero OS dialog needed when app owns files)
        val resolver = appContext.contentResolver
        val directlyDeletedIds = mutableListOf<Long>()
        val remainingUris = mutableListOf<Uri>()

        for (item in staged) {
            val uri = Uri.parse(item.contentUri)
            val deletedRows = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
            if (deletedRows > 0) {
                directlyDeletedIds.add(item.mediaStoreId)
            } else {
                remainingUris.add(uri)
            }
        }

        if (directlyDeletedIds.isNotEmpty() && remainingUris.isEmpty()) {
            val bytesReclaimed = staged.filter { it.mediaStoreId in directlyDeletedIds }.sumOf { it.sizeBytes }
            finalizeTrashDeletion(directlyDeletedIds, bytesReclaimed)
            onDirectComplete()
            return null
        }

        val pendingIntent = ScopedStorageTrashManager.createBatchTrashPendingIntent(
            contentResolver = resolver,
            uris = if (remainingUris.isNotEmpty()) remainingUris else uris
        )
        return pendingIntent?.intentSender
    }

    fun onSystemTrashRequestConfirmed() {
        val staged = uiState.value.stagedTrashItems
        val ids = staged.map { it.mediaStoreId }
        val bytes = staged.sumOf { it.sizeBytes }
        finalizeTrashDeletion(ids, bytes)
    }

    private fun finalizeTrashDeletion(deletedIds: List<Long>, bytesReclaimed: Long) {
        viewModelScope.launch {
            dao.deleteMediaByIds(deletedIds)
            val telemetry = dao.getScanTelemetry() ?: ScanTelemetryEntity()
            dao.upsertScanTelemetry(
                telemetry.copy(
                    lifetimeBytesReclaimed = telemetry.lifetimeBytesReclaimed + bytesReclaimed
                )
            )
            _snackbarMessage.value = "Moved ${deletedIds.size} items (${formatStorageSize(bytesReclaimed)}) to Trash!"
        }
    }

    // -----------------------------------------------------------------
    // Panda-Style Compressor Studio Controls
    // -----------------------------------------------------------------

    fun selectCompressionPreset(preset: CompressionPreset) {
        _compressionStudio.update {
            it.copy(
                selectedPreset = preset,
                targetRatioSlider = preset.defaultTargetRatio,
                customTargetMbInput = "",
                lastResult = null
            )
        }
    }

    fun updateCompressionRatioSlider(ratio: Float) {
        _compressionStudio.update {
            it.copy(
                selectedPreset = CompressionPreset.CUSTOM_SLIDER,
                targetRatioSlider = ratio.coerceIn(0.20f, 0.90f),
                lastResult = null
            )
        }
    }

    fun updateCustomTargetMbInput(input: String) {
        _compressionStudio.update {
            it.copy(
                selectedPreset = CompressionPreset.CUSTOM_SLIDER,
                customTargetMbInput = input,
                lastResult = null
            )
        }
    }

    fun toggleUseWebpForPhotos(enabled: Boolean) {
        _compressionStudio.update { it.copy(useWebpForPhotos = enabled) }
    }

    fun computeCurrentCompressionPlan(): CompressionPlan? {
        val studio = uiState.value.compressionStudio
        val item = studio.selectedItem ?: return null
        val customMb = studio.customTargetMbInput.toFloatOrNull()
        return PandaMediaCompressorEngine.calculateCompressionPlan(
            item = item,
            preset = studio.selectedPreset,
            sliderTargetRatio = studio.targetRatioSlider,
            customTargetMb = customMb
        )
    }

    fun executeCompression() {
        val studio = uiState.value.compressionStudio
        val item = studio.selectedItem ?: return
        val plan = computeCurrentCompressionPlan() ?: return
        if (studio.isCompressing) return

        viewModelScope.launch {
            if (!entitlementManager.isFeatureAllowed(PremiumFeature.UNLIMITED_BATCH_COMPRESS)) {
                _paywallPreview.value = PremiumFeature.UNLIMITED_BATCH_COMPRESS
                return@launch
            }

            _compressionStudio.update {
                it.copy(isCompressing = true, progressPercent = 8, lastResult = null)
            }

            val result = if (item.mediaType == com.example.data.local.MediaType.VIDEO) {
                PandaMediaCompressorEngine.compressVideoWithMedia3(
                    context = appContext,
                    item = item,
                    plan = plan,
                    onProgress = { pct ->
                        _compressionStudio.update { s -> s.copy(progressPercent = pct) }
                    }
                )
            } else {
                PandaMediaCompressorEngine.compressPhotoWithExifPreservation(
                    context = appContext,
                    item = item,
                    plan = plan,
                    useWebpLossy = studio.useWebpForPhotos,
                    onProgress = { pct ->
                        _compressionStudio.update { s -> s.copy(progressPercent = pct) }
                    }
                )
            }

            if (result is CompressionExecutionResult.Success) {
                val telemetry = dao.getScanTelemetry() ?: ScanTelemetryEntity()
                dao.upsertScanTelemetry(
                    telemetry.copy(
                        lifetimeBytesReclaimed = telemetry.lifetimeBytesReclaimed + result.savedBytes
                    )
                )
            }

            _compressionStudio.update {
                it.copy(isCompressing = false, progressPercent = 100, lastResult = result)
            }
        }
    }

    fun replaceOriginalAfterCompression(originalMediaId: Long) {
        viewModelScope.launch {
            dao.updateTriageStatus(originalMediaId, TriageStatus.STAGED_FOR_TRASH)
            _compressionStudio.update { it.copy(lastResult = null) }
            _snackbarMessage.value = "Compressed file kept • Original staged in Trash Bin"
        }
    }

    fun toggleProGateForTesting(unlocked: Boolean) {
        viewModelScope.launch {
            entitlementManager.setProUnlockedForTesting(unlocked)
            _snackbarMessage.value = if (unlocked) {
                "v1 Free Mode Active (All Pro features unlocked)"
            } else {
                "v2 Pro Gate Preview Mode Active"
            }
        }
    }

    fun dismissPaywallPreview() {
        _paywallPreview.value = null
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    companion object {
        fun formatStorageSize(bytes: Long): String {
            if (bytes <= 0L) return "0 MB"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                else -> String.format(Locale.US, "%.0f KB", kb)
            }
        }
    }
}
