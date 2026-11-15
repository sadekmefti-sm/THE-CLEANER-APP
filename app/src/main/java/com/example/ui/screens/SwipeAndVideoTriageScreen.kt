package com.example.ui.screens

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.MediaItemEntity
import com.example.data.local.TriageStatus
import com.example.ui.theme.CompressionCyan
import com.example.ui.theme.CrownAmberGold
import com.example.ui.theme.FavoritePurple
import com.example.ui.theme.KeepMintGreen
import com.example.ui.theme.OcrEmeraldShield
import com.example.ui.theme.TrashCoralRed
import com.example.ui.viewmodel.CleanVaultUiState
import com.example.ui.viewmodel.CleanVaultViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Phase 6A: Tinder-Style Photo Swipe Deck using Compose Animatable & pointerInput:
 * - Swipe Left = Stage for Trash
 * - Swipe Right = Keep
 * - Swipe Up = Favorite / Whitelist
 * - Includes Haptic feedback and an instant Undo button
 */
@Composable
fun TinderPhotoSwipeDeckScreen(
    uiState: CleanVaultUiState,
    onTriagePhoto: (MediaItemEntity, TriageStatus) -> Unit,
    onUndoLastSwipe: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val deck = uiState.swipePhotoDeck
    val topItem = deck.firstOrNull()
    val nextItem = deck.getOrNull(1)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val offsetX = remember(topItem?.mediaStoreId) { Animatable(0f) }
    val offsetY = remember(topItem?.mediaStoreId) { Animatable(0f) }

    fun commitTriageWithAnimation(item: MediaItemEntity, status: TriageStatus, targetX: Float, targetY: Float) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        scope.launch {
            launch { offsetX.animateTo(targetX, tween(220)) }
            launch { offsetY.animateTo(targetY, tween(220)) }
            onTriagePhoto(item, status)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Row with Undo & Staged Trash Counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("swipe_deck_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Photo Swipe Triage",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${deck.size} remaining • Left: Trash • Right: Keep • Up: Favorite",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onUndoLastSwipe,
                enabled = uiState.canUndoSwipe,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("swipe_undo_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Undo")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (topItem == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "All Photos Triaged!",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Staged Trash Queue: ${uiState.stagedTrashItems.size} items (${CleanVaultViewModel.formatStorageSize(uiState.stagedTrashBytes)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }

        // Card Stack Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background Next Card Preview
            if (nextItem != null) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .graphicsLayer {
                            scaleX = 0.94f
                            scaleY = 0.94f
                            alpha = 0.6f
                        }
                ) {
                    AsyncImage(
                        model = Uri.parse(nextItem.contentUri),
                        contentDescription = nextItem.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Active Top Swipeable Card
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .graphicsLayer {
                        rotationZ = (offsetX.value / 28f).coerceIn(-18f, 18f)
                    }
                    .pointerInput(topItem.mediaStoreId) {
                        detectDragGestures(
                            onDragEnd = {
                                val dx = offsetX.value
                                val dy = offsetY.value
                                when {
                                    dx < -240f -> commitTriageWithAnimation(
                                        topItem,
                                        TriageStatus.STAGED_FOR_TRASH,
                                        -1200f,
                                        dy
                                    )
                                    dx > 240f -> commitTriageWithAnimation(
                                        topItem,
                                        TriageStatus.KEPT,
                                        1200f,
                                        dy
                                    )
                                    dy < -240f && abs(dx) < 220f -> commitTriageWithAnimation(
                                        topItem,
                                        TriageStatus.FAVORITED,
                                        dx,
                                        -1200f
                                    )
                                    else -> {
                                        scope.launch {
                                            launch { offsetX.animateTo(0f, tween(200)) }
                                            launch { offsetY.animateTo(0f, tween(200)) }
                                        }
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                    offsetY.snapTo(offsetY.value + dragAmount.y)
                                }
                            }
                        )
                    }
                    .testTag("top_swipe_photo_card")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = Uri.parse(topItem.contentUri),
                        contentDescription = topItem.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // Top Badges (Size + Best Shot / OCR Shield)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${CleanVaultViewModel.formatStorageSize(topItem.sizeBytes)} • ${topItem.width}x${topItem.height}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        if (topItem.isDocumentOrReceipt) {
                            Surface(
                                color = OcrEmeraldShield,
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "OCR Guardrail",
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "OCR PROTECTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Dynamic Gesture Stamp Overlay
                    val dx = offsetX.value
                    val dy = offsetY.value
                    when {
                        dx > 90f -> SwipeStampBadge("KEEP", KeepMintGreen, Modifier.align(Alignment.Center))
                        dx < -90f -> SwipeStampBadge("STAGE TRASH", TrashCoralRed, Modifier.align(Alignment.Center))
                        dy < -90f -> SwipeStampBadge("FAVORITE", FavoritePurple, Modifier.align(Alignment.Center))
                    }

                    // Bottom Metadata Telemetry
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = topItem.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Composite Score: ${topItem.compositeBestShotScore.toInt()}/100 • Sharpness: ${topItem.sharpnessScore.toInt()} • Folder: ${topItem.bucketName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Accessible 48dp+ Action Buttons below deck
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { commitTriageWithAnimation(topItem, TriageStatus.STAGED_FOR_TRASH, -1000f, 0f) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = TrashCoralRed.copy(alpha = 0.2f),
                    contentColor = TrashCoralRed
                ),
                modifier = Modifier
                    .size(64.dp)
                    .testTag("swipe_action_trash_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Stage Trash", modifier = Modifier.size(30.dp))
            }

            FilledIconButton(
                onClick = { commitTriageWithAnimation(topItem, TriageStatus.FAVORITED, 0f, -1000f) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = FavoritePurple.copy(alpha = 0.2f),
                    contentColor = FavoritePurple
                ),
                modifier = Modifier
                    .size(56.dp)
                    .testTag("swipe_action_favorite_button")
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Favorite", modifier = Modifier.size(26.dp))
            }

            FilledIconButton(
                onClick = { commitTriageWithAnimation(topItem, TriageStatus.KEPT, 1000f, 0f) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = KeepMintGreen.copy(alpha = 0.2f),
                    contentColor = KeepMintGreen
                ),
                modifier = Modifier
                    .size(64.dp)
                    .testTag("swipe_action_keep_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = "Keep Photo", modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun SwipeStampBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.24f),
        border = BorderStroke(3.dp, color),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineLarge,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

/**
 * Phase 6B: TikTok-Style Vertical Video Triage using VerticalPager + shared Media3 ExoPlayer instance:
 * - Muted by default with one-tap audio toggle
 * - Prominent file-size & resolution badge (e.g. "1.4 GB • 1080p")
 * - One-tap "Compress (Save ~65%)", "Stage Trash", and "Keep" buttons
 */
@Composable
fun TikTokVideoTriageScreen(
    uiState: CleanVaultUiState,
    onOpenCompressorForVideo: (MediaItemEntity) -> Unit,
    onTriageVideo: (MediaItemEntity, TriageStatus) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val videos = uiState.videoTriageFeed
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(true) }

    if (videos.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No Videos in Triage Queue", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap 'Seed Sample Gallery' on the Dashboard to generate a demo MP4 video clip or scan your device videos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back to Dashboard") }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { videos.size })

    // Shared single ExoPlayer instance across the VerticalPager to keep memory flat
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f // Muted by default
        }
    }

    val currentVideo = videos.getOrNull(pagerState.currentPage) ?: videos.first()

    LaunchedEffect(currentVideo.contentUri) {
        runCatching {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(currentVideo.contentUri)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("tiktok_video_vertical_pager")
        ) { pageIndex ->
            val videoItem = videos[pageIndex]
            val estimatedSaveMb = CleanVaultViewModel.formatStorageSize((videoItem.sizeBytes * 0.65).toLong())

            Box(modifier = Modifier.fillMaxSize()) {
                if (pageIndex == pagerState.currentPage) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                player = exoPlayer
                            }
                        },
                        update = { view ->
                            view.player = exoPlayer
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { isMuted = !isMuted }
                    )
                } else {
                    AsyncImage(
                        model = Uri.parse(videoItem.contentUri),
                        contentDescription = videoItem.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom-to-Top Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                )

                // Prominent File-Size & Resolution Badge
                Surface(
                    color = CompressionCyan,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 18.dp, end = 18.dp)
                ) {
                    Text(
                        text = "${CleanVaultViewModel.formatStorageSize(videoItem.sizeBytes)} • ${videoItem.height}p",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Bottom Overlay Metadata & One-Tap Actions
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = videoItem.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Swipe vertically for next clip • ${pageIndex + 1} of ${videos.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.16f), CircleShape)
                                .testTag("video_mute_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onOpenCompressorForVideo(videoItem) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CompressionCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(50.dp)
                                .testTag("video_compress_70_button")
                        ) {
                            Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Compress (Save $estimatedSaveMb)",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { onTriageVideo(videoItem, TriageStatus.STAGED_FOR_TRASH) },
                            colors = ButtonDefaults.buttonColors(containerColor = TrashCoralRed),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(50.dp)
                                .testTag("video_trash_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Trash")
                        }

                        OutlinedButton(
                            onClick = { onTriageVideo(videoItem, TriageStatus.KEPT) },
                            border = BorderStroke(1.5.dp, KeepMintGreen),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KeepMintGreen),
                            modifier = Modifier
                                .weight(0.8f)
                                .height(50.dp)
                                .testTag("video_keep_button")
                        ) {
                            Text("Keep", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Top-Left Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .testTag("video_triage_back_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}
