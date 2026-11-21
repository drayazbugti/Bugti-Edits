package com.example.ui.screens

import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.media.VideoEnhancerEngine
import com.example.model.EditorMainMode
import com.example.model.ExportResolution
import com.example.model.MediaSource
import com.example.model.StudioCatalog
import com.example.model.StudioEditState
import com.example.model.StudioToolId
import com.example.ui.components.EditorContextualToolPanel
import com.example.ui.components.toolIconFor
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SurfaceBright
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOnWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    onBackClick: () -> Unit,
    onAddClipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val mediaSource = uiState.activeMediaSource
    val isVideo = mediaSource is MediaSource.ContentUri && mediaSource.isVideo

    var isFullscreen by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // Video playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    val totalDurationMs = uiState.videoDurationMs.coerceAtLeast(1000L)

    val exoPlayer = remember(mediaSource) {
        if (mediaSource is MediaSource.ContentUri && mediaSource.isVideo) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(mediaSource.uri))
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                prepare()
            }
        } else null
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.release()
        }
    }

    // Apply live Media3 effects whenever edits change for video
    LaunchedEffect(
        exoPlayer,
        uiState.editState.enhanceMode,
        uiState.editState.enhanceStrength,
        uiState.editState.filterPreset,
        uiState.editState.filterIntensity,
        uiState.editState.brightness,
        uiState.editState.contrast,
        uiState.editState.saturation,
        uiState.editState.warmth,
        uiState.editState.rotationDegrees,
        uiState.editState.flipHorizontal,
        uiState.editState.faceGlow,
        uiState.editState.hairGloss,
        uiState.editState.makeupTint,
        uiState.isHoldingCompare
    ) {
        exoPlayer?.let { player ->
            val effects = if (uiState.isHoldingCompare) {
                emptyList()
            } else {
                VideoEnhancerEngine.buildMedia3Effects(uiState.editState, isExport = false)
            }
            player.setVideoEffects(effects)
        }
    }

    LaunchedEffect(exoPlayer, uiState.editState.playbackSpeed, uiState.editState.muteAudio, uiState.editState.audioVolume) {
        exoPlayer?.let { player ->
            player.playbackParameters = PlaybackParameters(uiState.editState.playbackSpeed)
            player.volume = if (uiState.editState.muteAudio) 0f else uiState.editState.audioVolume
        }
    }

    // Poll playback position
    LaunchedEffect(exoPlayer, isPlaying) {
        while (exoPlayer != null) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(120L)
        }
    }

    // Simulated scrubbing timeline position for photo mode
    var photoTimelineFraction by remember { mutableFloatStateOf(0.08f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ==================== TOP BAR ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("editor_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                if (uiState.canUndo) {
                    IconButton(
                        onClick = { viewModel.undoLastEdit() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo last edit",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Hold-to-Compare pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    viewModel.setHoldingCompare(true)
                                    tryAwaitRelease()
                                    viewModel.setHoldingCompare(false)
                                }
                            )
                        }
                        .testTag("editor_compare_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Compare,
                            contentDescription = "Hold to compare original",
                            tint = if (uiState.isHoldingCompare) AccentTeal else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (uiState.isHoldingCompare) "Original" else "Compare",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isHoldingCompare) AccentTeal else TextSecondary
                        )
                    }
                }

                // White "Save" Pill matching Screenshots 1, 2, 4
                Button(
                    onClick = { viewModel.exportCurrentCreation() },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceBright,
                        contentColor = TextOnWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("editor_save_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Overflow Menu (...)
                Box {
                    Surface(
                        onClick = { showOverflowMenu = true },
                        shape = RoundedCornerShape(999.dp),
                        color = SurfaceBright,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("editor_more_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More export options",
                                tint = TextOnWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        containerColor = SurfaceCard
                    ) {
                        ExportResolution.entries.forEach { res ->
                            val selected = res == uiState.editState.exportResolution
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${if (selected) "✓ " else ""}Export: ${res.label}",
                                        color = if (selected) AccentTeal else TextPrimary
                                    )
                                },
                                onClick = {
                                    viewModel.setExportResolution(res)
                                    showOverflowMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Reset All Edits", color = TextPrimary) },
                            onClick = {
                                viewModel.resetAllEdits()
                                showOverflowMenu = false
                            }
                        )
                    }
                }
            }
        }

        // ==================== MAIN MEDIA CANVAS ====================
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = if (isFullscreen) 0.dp else 14.dp),
            contentAlignment = Alignment.Center
        ) {
            val canvasWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val canvasHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

            if (isVideo && exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        if (view.player != exoPlayer) view.player = exoPlayer
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 14.dp))
                )
            } else {
                val displayBitmap: Bitmap? = if (uiState.isHoldingCompare) {
                    uiState.originalBitmap
                } else {
                    uiState.processedBitmap ?: uiState.originalBitmap
                }

                if (displayBitmap != null) {
                    val activeBrushTool = uiState.selectedEditorTool
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 14.dp))
                            .pointerInput(activeBrushTool) {
                                if (activeBrushTool == StudioToolId.MOSAIC ||
                                    activeBrushTool == StudioToolId.REMOVAL ||
                                    activeBrushTool == StudioToolId.AI_REMOVAL
                                ) {
                                    detectTapGestures { offset ->
                                        val nx = offset.x / size.width.toFloat()
                                        val ny = offset.y / size.height.toFloat()
                                        if (activeBrushTool == StudioToolId.MOSAIC) {
                                            viewModel.addMosaicPoint(nx, ny)
                                        } else {
                                            viewModel.addRemovalPoint(nx, ny)
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = displayBitmap.asImageBitmap(),
                            contentDescription = "Studio edited preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (uiState.isMagnifierEnabled) {
                                        scaleX = 2.0f
                                        scaleY = 2.0f
                                    }
                                }
                        )
                    }
                } else if (uiState.isProcessingPreview) {
                    CircularProgressIndicator(color = SurfaceBright)
                }
            }

            // Draggable Text & Sticker Overlays on Canvas
            uiState.editState.textOverlays.forEach { item ->
                DraggableCanvasChip(
                    normX = item.normX,
                    normY = item.normY,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onDrag = { dx, dy ->
                        val nextX = item.normX + dx / canvasWidthPx.coerceAtLeast(1f)
                        val nextY = item.normY + dy / canvasHeightPx.coerceAtLeast(1f)
                        viewModel.moveTextOverlay(item.id, nextX, nextY)
                    },
                    onRemove = { viewModel.removeTextOverlay(item.id) }
                ) {
                    Text(
                        text = item.text,
                        color = Color(item.colorArgb),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            uiState.editState.stickerOverlays.forEach { sticker ->
                DraggableCanvasChip(
                    normX = sticker.normX,
                    normY = sticker.normY,
                    canvasWidthPx = canvasWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    onDrag = { dx, dy ->
                        val nextX = sticker.normX + dx / canvasWidthPx.coerceAtLeast(1f)
                        val nextY = sticker.normY + dy / canvasHeightPx.coerceAtLeast(1f)
                        viewModel.moveStickerOverlay(sticker.id, nextX, nextY)
                    },
                    onRemove = { viewModel.removeStickerOverlay(sticker.id) }
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xCC121112),
                        border = BorderStroke(1.dp, BorderMedium)
                    ) {
                        Text(
                            text = sticker.label,
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Active Enhancement / Magnifier floating badge
            if (uiState.isMagnifierEnabled) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xCC121112),
                    border = BorderStroke(1.dp, AccentTeal)
                ) {
                    Text(
                        text = "2.0x HD Loupe Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentTeal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            if (uiState.isProcessingPreview && uiState.processedBitmap != null) {
                CircularProgressIndicator(
                    color = SurfaceBright,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
        }

        // ==================== PLAYBACK / SCRUB BAR (Screenshots 1, 2, 4) ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isVideo && exoPlayer != null) {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    } else {
                        isPlaying = !isPlaying
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("editor_play_pause_btn")
            ) {
                Icon(
                    imageVector = if (isVideo && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = TextPrimary
                )
            }

            val currentSec = if (isVideo) currentPosMs / 1000 else (photoTimelineFraction * 19).roundToInt().toLong()
            val totalSec = if (isVideo) totalDurationMs / 1000 else 19L
            Text(
                text = "${formatSeconds(currentSec)}/${formatSeconds(totalSec)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                modifier = Modifier.padding(end = 10.dp)
            )

            val sliderFraction = if (isVideo) {
                (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                photoTimelineFraction
            }
            Slider(
                value = sliderFraction,
                onValueChange = { f ->
                    if (isVideo && exoPlayer != null) {
                        exoPlayer.seekTo((f * totalDurationMs).toLong())
                    } else {
                        photoTimelineFraction = f
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = SurfaceBright,
                    activeTrackColor = SurfaceBright.copy(alpha = 0.7f),
                    inactiveTrackColor = Color(0xFF3A373A)
                ),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { isFullscreen = !isFullscreen },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("editor_fullscreen_btn")
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = "Toggle fullscreen preview",
                    tint = TextPrimary
                )
            }
        }

        if (!isFullscreen) {
            // ==================== TIMELINE FILMSTRIP (Screenshots 1, 2, 4) ====================
            EditorTimelineStrip(
                isVideo = isVideo,
                frames = uiState.videoFrames,
                previewBitmap = uiState.processedBitmap ?: uiState.originalBitmap,
                selectedCoverIndex = uiState.selectedCoverFrameIndex,
                playheadFraction = if (isVideo) (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else photoTimelineFraction,
                onAddClipClick = onAddClipClick,
                onCoverClick = {
                    val nextIdx = if (uiState.videoFrames.isEmpty()) 0 else (uiState.selectedCoverFrameIndex + 1) % uiState.videoFrames.size
                    viewModel.selectCoverFrame(nextIdx)
                }
            )

            // ==================== CONTEXTUAL TOOL PANEL ====================
            AnimatedVisibility(
                visible = uiState.selectedEditorTool != null && uiState.selectedEditorTool != StudioToolId.MAGNIFIER,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.selectedEditorTool?.let { activeTool ->
                    EditorContextualToolPanel(
                        tool = activeTool,
                        isVideo = isVideo,
                        editState = uiState.editState,
                        onUpdateEditState = { rec, fn -> viewModel.updateEditState(rec, fn) },
                        onSelectEnhanceMode = { viewModel.setEnhanceMode(it) },
                        onSelectFilter = { viewModel.setFilterPreset(it) },
                        onSelectEffect = { viewModel.setEffectMode(it) },
                        onSelectOverlay = { viewModel.setOverlayMode(it) },
                        onSelectAspectRatio = { viewModel.setAspectRatio(it) },
                        onSelectFrame = { viewModel.setFrameStyle(it) },
                        onRotate90 = { viewModel.rotate90() },
                        onFlipHorizontal = { viewModel.toggleFlipHorizontal() },
                        onAddText = { txt, col -> viewModel.addTextOverlay(txt, col) },
                        onAddSticker = { viewModel.addStickerOverlay(it) },
                        onClearMosaic = { viewModel.clearMosaicPoints() },
                        onClearRemoval = { viewModel.clearRemovalPoints() },
                        onApplyTemplate = { viewModel.applyTemplateInEditor(it) },
                        onClosePanel = { viewModel.selectEditorTool(activeTool) }
                    )
                }
            }

            // ==================== SCROLLABLE TOOL ICON ROW ====================
            val visibleTools = if (uiState.editorMainMode == EditorMainMode.EDIT) {
                StudioCatalog.editorEditTools
            } else {
                StudioCatalog.editorRetouchTools
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A090A))
                    .padding(top = 8.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(visibleTools, key = { it.name }) { tool ->
                    val isSelected = tool == uiState.selectedEditorTool
                    Column(
                        modifier = Modifier
                            .width(66.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) SurfaceElevated else Color.Transparent)
                            .clickable { viewModel.selectEditorTool(tool) }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .testTag("editor_tool_${tool.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = toolIconFor(tool),
                            contentDescription = tool.label,
                            tint = if (isSelected) SurfaceBright else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = tool.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isSelected) TextPrimary else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            // ==================== RETOUCH | EDIT BOTTOM SWITCHER ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A090A))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorBottomModeTab(
                    label = stringResource(R.string.action_retouch),
                    selected = uiState.editorMainMode == EditorMainMode.RETOUCH,
                    testTag = "editor_mode_retouch",
                    onClick = { viewModel.switchEditorMainMode(EditorMainMode.RETOUCH) }
                )
                Spacer(modifier = Modifier.width(42.dp))
                EditorBottomModeTab(
                    label = stringResource(R.string.action_edit),
                    selected = uiState.editorMainMode == EditorMainMode.EDIT,
                    testTag = "editor_mode_edit",
                    onClick = { viewModel.switchEditorMainMode(EditorMainMode.EDIT) }
                )
            }
        }
    }
}

@Composable
private fun EditorTimelineStrip(
    isVideo: Boolean,
    frames: List<Bitmap>,
    previewBitmap: Bitmap?,
    selectedCoverIndex: Int,
    playheadFraction: Float,
    onAddClipClick: () -> Unit,
    onCoverClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // + Add Clip tile
        Surface(
            onClick = onAddClipClick,
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2B292B),
            modifier = Modifier
                .size(44.dp)
                .testTag("editor_add_clip_btn")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Change or add media clip",
                    tint = TextPrimary
                )
            }
        }

        // Cover tile
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                .clickable(onClick = onCoverClick)
                .testTag("editor_cover_btn")
        ) {
            val coverBmp = frames.getOrNull(selectedCoverIndex) ?: previewBitmap
            if (coverBmp != null) {
                Image(
                    bitmap = coverBmp.asImageBitmap(),
                    contentDescription = "Cover frame",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
            )
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.label_cover),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextPrimary
                )
            }
        }

        // Keyframe Filmstrip with white border & vertical white playhead
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, SurfaceBright, RoundedCornerShape(10.dp))
                .background(SurfaceElevated)
        ) {
            val stripWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            Row(modifier = Modifier.fillMaxSize()) {
                val frameCount = 6
                for (i in 0 until frameCount) {
                    val bmp = if (isVideo && frames.isNotEmpty()) {
                        frames[(i * frames.size) / frameCount]
                    } else {
                        previewBitmap
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (i % 2 == 0) SurfaceCard else SurfaceElevated)
                        )
                    }
                }
            }

            // White Playhead bar
            Box(
                modifier = Modifier
                    .offset { IntOffset((stripWidthPx * playheadFraction).roundToInt() - 2, 0) }
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(SurfaceBright)
            )
        }
    }
}

@Composable
private fun EditorBottomModeTab(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TextPrimary else TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) SurfaceBright else Color.Transparent)
        )
    }
}

@Composable
private fun DraggableCanvasChip(
    normX: Float,
    normY: Float,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onDrag: (Float, Float) -> Unit,
    onRemove: () -> Unit,
    content: @Composable () -> Unit
) {
    val px = ((normX - 0.5f) * canvasWidthPx).roundToInt()
    val py = ((normY - 0.5f) * canvasHeightPx).roundToInt()
    Row(
        modifier = Modifier
            .offset { IntOffset(px, py) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xBB121112))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove overlay",
                tint = TextPrimary,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

private fun formatSeconds(sec: Long): String {
    val s = sec.coerceAtLeast(0L)
    val m = s / 60
    val rem = s % 60
    return "%02d:%02d".format(m, rem)
}
