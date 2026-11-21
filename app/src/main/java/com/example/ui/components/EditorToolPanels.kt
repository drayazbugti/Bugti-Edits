package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AspectRatioOption
import com.example.model.EffectMode
import com.example.model.EnhanceMode
import com.example.model.FilterPreset
import com.example.model.FrameStyle
import com.example.model.OverlayMode
import com.example.model.StudioCatalog
import com.example.model.StudioEditState
import com.example.model.StudioTemplate
import com.example.model.StudioToolId
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SurfaceBright
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfacePill
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOnWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EditorContextualToolPanel(
    tool: StudioToolId,
    isVideo: Boolean,
    editState: StudioEditState,
    onUpdateEditState: (Boolean, (StudioEditState) -> StudioEditState) -> Unit,
    onSelectEnhanceMode: (EnhanceMode) -> Unit,
    onSelectFilter: (FilterPreset) -> Unit,
    onSelectEffect: (EffectMode) -> Unit,
    onSelectOverlay: (OverlayMode) -> Unit,
    onSelectAspectRatio: (AspectRatioOption) -> Unit,
    onSelectFrame: (FrameStyle) -> Unit,
    onRotate90: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onAddText: (String, Long) -> Unit,
    onAddSticker: (String) -> Unit,
    onClearMosaic: () -> Unit,
    onClearRemoval: () -> Unit,
    onApplyTemplate: (StudioTemplate) -> Unit,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onClosePanel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close tool panel", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            when (tool) {
                StudioToolId.ENHANCE -> EnhancePanel(
                    editState = editState,
                    onSelectEnhanceMode = onSelectEnhanceMode,
                    onUpdateStrength = { v -> onUpdateEditState(false) { it.copy(enhanceStrength = v) } }
                )

                StudioToolId.FILTERS, StudioToolId.COLOR_MATCH -> FiltersPanel(
                    editState = editState,
                    onSelectFilter = onSelectFilter,
                    onUpdateIntensity = { v -> onUpdateEditState(false) { it.copy(filterIntensity = v) } }
                )

                StudioToolId.ADJUST -> AdjustPanel(
                    editState = editState,
                    onUpdateEditState = onUpdateEditState
                )

                StudioToolId.EDIT -> ClipEditPanel(
                    isVideo = isVideo,
                    editState = editState,
                    onRotate90 = onRotate90,
                    onFlipHorizontal = onFlipHorizontal,
                    onUpdateEditState = onUpdateEditState
                )

                StudioToolId.EFFECTS -> EffectsPanel(
                    editState = editState,
                    onSelectEffect = onSelectEffect,
                    onUpdateIntensity = { v -> onUpdateEditState(false) { it.copy(effectIntensity = v) } }
                )

                StudioToolId.OVERLAY -> OverlaysPanel(
                    editState = editState,
                    onSelectOverlay = onSelectOverlay,
                    onUpdateIntensity = { v -> onUpdateEditState(false) { it.copy(overlayIntensity = v) } }
                )

                StudioToolId.TEXT -> TextToolPanel(onAddText = onAddText)

                StudioToolId.STICKERS -> StickersToolPanel(onAddSticker = onAddSticker)

                StudioToolId.FRAMES -> FramesToolPanel(
                    selected = editState.frameStyle,
                    onSelect = onSelectFrame
                )

                StudioToolId.CANVAS -> CanvasRatioPanel(
                    selected = editState.aspectRatio,
                    onSelect = onSelectAspectRatio
                )

                StudioToolId.AUDIO -> AudioToolPanel(
                    isVideo = isVideo,
                    editState = editState,
                    onUpdateEditState = onUpdateEditState
                )

                StudioToolId.MOSAIC -> TouchBrushPanel(
                    hint = if (isVideo) "Mosaic privacy blur is optimized for Photo edits" else "Tap or drag on the photo above to pixelate areas",
                    count = editState.mosaicPoints.size,
                    onClear = onClearMosaic
                )

                StudioToolId.REMOVAL, StudioToolId.AI_REMOVAL -> TouchBrushPanel(
                    hint = if (isVideo) "Object removal heal brush works on Photo frames" else "Tap spots on the photo above to softly heal & remove blemishes",
                    count = editState.removalPoints.size,
                    onClear = onClearRemoval
                )

                StudioToolId.TEMPLATE -> TemplateQuickPanel(onApplyTemplate = onApplyTemplate)

                StudioToolId.FACE,
                StudioToolId.SKIN_SMOOTH,
                StudioToolId.BRIGHTEN,
                StudioToolId.AI_RETOUCH,
                StudioToolId.BODY,
                StudioToolId.HAIR,
                StudioToolId.MAKEUP -> RetouchPanel(
                    activeTool = tool,
                    editState = editState,
                    onUpdateEditState = onUpdateEditState
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun EnhancePanel(
    editState: StudioEditState,
    onSelectEnhanceMode: (EnhanceMode) -> Unit,
    onUpdateStrength: (Float) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(EnhanceMode.entries) { mode ->
            val selected = mode == editState.enhanceMode
            Surface(
                onClick = { onSelectEnhanceMode(mode) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) SurfaceBright else SurfaceElevated,
                border = BorderStroke(1.dp, if (selected) SurfaceBright else BorderSubtle),
                modifier = Modifier.testTag("editor_enhance_${mode.name.lowercase()}")
            ) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) TextOnWhite else TextPrimary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
    if (editState.enhanceMode != EnhanceMode.NONE) {
        LabeledStudioSlider(
            label = "Clarity Strength",
            value = editState.enhanceStrength,
            valueRange = 0.1f..1f,
            displayValue = "${(editState.enhanceStrength * 100).toInt()}%",
            onValueChange = onUpdateStrength
        )
    }
}

@Composable
private fun FiltersPanel(
    editState: StudioEditState,
    onSelectFilter: (FilterPreset) -> Unit,
    onUpdateIntensity: (Float) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FilterPreset.entries) { preset ->
            val selected = preset == editState.filterPreset
            Surface(
                onClick = { onSelectFilter(preset) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) SurfaceBright else SurfaceElevated,
                border = BorderStroke(1.dp, if (selected) SurfaceBright else BorderSubtle),
                modifier = Modifier.testTag("editor_filter_${preset.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(preset.accentHex))
                    )
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) TextOnWhite else TextPrimary
                    )
                }
            }
        }
    }
    if (editState.filterPreset != FilterPreset.NATURAL) {
        LabeledStudioSlider(
            label = "Intensity",
            value = editState.filterIntensity,
            valueRange = 0f..1f,
            displayValue = "${(editState.filterIntensity * 100).toInt()}%",
            onValueChange = onUpdateIntensity
        )
    }
}

@Composable
private fun AdjustPanel(
    editState: StudioEditState,
    onUpdateEditState: (Boolean, (StudioEditState) -> StudioEditState) -> Unit
) {
    var activeSlider by remember { mutableStateOf("Brightness") }
    val options = listOf("Brightness", "Contrast", "Saturation", "Sharpen", "Warmth", "Vignette")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { opt ->
            val selected = opt == activeSlider
            Surface(
                onClick = { activeSlider = opt },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) SurfaceBright else SurfacePill
            ) {
                Text(
                    text = opt,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) TextOnWhite else TextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    when (activeSlider) {
        "Brightness" -> LabeledStudioSlider("Brightness", editState.brightness, -0.5f..0.5f, "${(editState.brightness * 200).toInt()}") { v ->
            onUpdateEditState(false) { it.copy(brightness = v) }
        }
        "Contrast" -> LabeledStudioSlider("Contrast", editState.contrast, -0.5f..0.5f, "${(editState.contrast * 200).toInt()}") { v ->
            onUpdateEditState(false) { it.copy(contrast = v) }
        }
        "Saturation" -> LabeledStudioSlider("Saturation", editState.saturation, -0.5f..0.5f, "${(editState.saturation * 200).toInt()}") { v ->
            onUpdateEditState(false) { it.copy(saturation = v) }
        }
        "Sharpen" -> LabeledStudioSlider("Sharpen", editState.sharpen, 0f..1f, "${(editState.sharpen * 100).toInt()}%") { v ->
            onUpdateEditState(false) { it.copy(sharpen = v) }
        }
        "Warmth" -> LabeledStudioSlider("Warmth", editState.warmth, -0.5f..0.5f, "${(editState.warmth * 200).toInt()}") { v ->
            onUpdateEditState(false) { it.copy(warmth = v) }
        }
        "Vignette" -> LabeledStudioSlider("Vignette", editState.vignette, 0f..1f, "${(editState.vignette * 100).toInt()}%") { v ->
            onUpdateEditState(false) { it.copy(vignette = v) }
        }
    }
}

@Composable
private fun ClipEditPanel(
    isVideo: Boolean,
    editState: StudioEditState,
    onRotate90: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onUpdateEditState: (Boolean, (StudioEditState) -> StudioEditState) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = onRotate90,
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            modifier = Modifier.weight(1f).testTag("editor_rotate_btn")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.RotateRight, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rotate 90°", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            }
        }
        Surface(
            onClick = onFlipHorizontal,
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            modifier = Modifier.weight(1f).testTag("editor_flip_btn")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Flip, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mirror", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            }
        }
    }

    if (isVideo) {
        Text(
            text = "Clip Trim Range (${(editState.trimStartFraction * 100).toInt()}% – ${(editState.trimEndFraction * 100).toInt()}%)",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        RangeSlider(
            value = editState.trimStartFraction..editState.trimEndFraction,
            onValueChange = { range ->
                val start = range.start.coerceIn(0f, 0.9f)
                val end = range.endInclusive.coerceAtLeast(start + 0.08f).coerceAtMost(1f)
                onUpdateEditState(false) { it.copy(trimStartFraction = start, trimEndFraction = end) }
            },
            colors = SliderDefaults.colors(
                thumbColor = SurfaceBright,
                activeTrackColor = SurfaceBright,
                inactiveTrackColor = SurfacePill
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.5f, 1f, 1.5f, 2f).forEach { speed ->
                val selected = editState.playbackSpeed == speed
                Surface(
                    onClick = { onUpdateEditState(true) { it.copy(playbackSpeed = speed) } },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) SurfaceBright else SurfacePill,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "${speed}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) TextOnWhite else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectsPanel(
    editState: StudioEditState,
    onSelectEffect: (EffectMode) -> Unit,
    onUpdateIntensity: (Float) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(EffectMode.entries) { mode ->
            val selected = mode == editState.effectMode
            Surface(
                onClick = { onSelectEffect(mode) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) SurfaceBright else SurfaceElevated
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) TextOnWhite else TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
    if (editState.effectMode != EffectMode.NONE) {
        LabeledStudioSlider(
            label = "Effect Strength",
            value = editState.effectIntensity,
            valueRange = 0.1f..1f,
            displayValue = "${(editState.effectIntensity * 100).toInt()}%",
            onValueChange = onUpdateIntensity
        )
    }
}

@Composable
private fun OverlaysPanel(
    editState: StudioEditState,
    onSelectOverlay: (OverlayMode) -> Unit,
    onUpdateIntensity: (Float) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(OverlayMode.entries) { mode ->
            val selected = mode == editState.overlayMode
            Surface(
                onClick = { onSelectOverlay(mode) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) SurfaceBright else SurfaceElevated
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) TextOnWhite else TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
    if (editState.overlayMode != OverlayMode.NONE) {
        LabeledStudioSlider(
            label = "Blend Opacity",
            value = editState.overlayIntensity,
            valueRange = 0.1f..1f,
            displayValue = "${(editState.overlayIntensity * 100).toInt()}%",
            onValueChange = onUpdateIntensity
        )
    }
}

@Composable
private fun TextToolPanel(
    onAddText: (String, Long) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(StudioCatalog.textColorChoices.first()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Enter title or caption...", color = TextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceElevated,
                unfocusedContainerColor = SurfaceElevated,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = SurfaceBright,
                unfocusedBorderColor = BorderSubtle
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("editor_text_input")
        )
        Button(
            onClick = {
                if (draft.isNotBlank()) {
                    onAddText(draft, selectedColor.toArgb().toLong() and 0xFFFFFFFFL)
                    draft = ""
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceBright, contentColor = TextOnWhite),
            modifier = Modifier.height(52.dp).testTag("editor_add_text_btn")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add text overlay")
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Color:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        StudioCatalog.textColorChoices.forEach { c ->
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                        width = if (c == selectedColor) 2.5.dp else 1.dp,
                        color = if (c == selectedColor) AccentTeal else BorderSubtle,
                        shape = CircleShape
                    )
                    .clickable { selectedColor = c }
            )
        }
    }
}

@Composable
private fun StickersToolPanel(onAddSticker: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StudioCatalog.stickerChoices) { sticker ->
            Surface(
                onClick = { onAddSticker(sticker) },
                shape = RoundedCornerShape(999.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.testTag("editor_sticker_${sticker.hashCode()}")
            ) {
                Text(
                    text = sticker,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
    Text(
        text = "Tap a studio badge to add, then drag on canvas",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted
    )
}

@Composable
private fun FramesToolPanel(
    selected: FrameStyle,
    onSelect: (FrameStyle) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FrameStyle.entries) { frame ->
            val isSel = frame == selected
            Surface(
                onClick = { onSelect(frame) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSel) SurfaceBright else SurfaceElevated
            ) {
                Text(
                    text = frame.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) TextOnWhite else TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun CanvasRatioPanel(
    selected: AspectRatioOption,
    onSelect: (AspectRatioOption) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AspectRatioOption.entries) { ratio ->
            val isSel = ratio == selected
            Surface(
                onClick = { onSelect(ratio) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSel) SurfaceBright else SurfaceElevated
            ) {
                Text(
                    text = ratio.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) TextOnWhite else TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun AudioToolPanel(
    isVideo: Boolean,
    editState: StudioEditState,
    onUpdateEditState: (Boolean, (StudioEditState) -> StudioEditState) -> Unit
) {
    if (!isVideo) {
        Text(
            text = "Audio controls apply when editing a video clip.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            onClick = { onUpdateEditState(true) { it.copy(muteAudio = !it.muteAudio) } },
            shape = RoundedCornerShape(14.dp),
            color = if (editState.muteAudio) SurfaceBright else SurfaceElevated,
            modifier = Modifier.testTag("editor_mute_toggle")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (editState.muteAudio) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = "Mute audio",
                    tint = if (editState.muteAudio) TextOnWhite else TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (editState.muteAudio) "Muted" else "Audio On",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (editState.muteAudio) TextOnWhite else TextPrimary
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LabeledStudioSlider(
                label = "Volume",
                value = if (editState.muteAudio) 0f else editState.audioVolume,
                valueRange = 0f..1f,
                displayValue = "${((if (editState.muteAudio) 0f else editState.audioVolume) * 100).toInt()}%",
                onValueChange = { v ->
                    onUpdateEditState(false) { it.copy(audioVolume = v, muteAudio = v < 0.02f) }
                }
            )
        }
    }
}

@Composable
private fun TouchBrushPanel(
    hint: String,
    count: Int,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            TextButton(onClick = onClear) {
                Text("Clear ($count)", color = AccentTeal)
            }
        }
    }
}

@Composable
private fun TemplateQuickPanel(onApplyTemplate: (StudioTemplate) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(StudioCatalog.templates, key = { it.id }) { tpl ->
            Surface(
                onClick = { onApplyTemplate(tpl) },
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.width(124.dp)
            ) {
                Column {
                    Image(
                        painter = painterResource(id = tpl.previewRes),
                        contentDescription = tpl.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    )
                    Text(
                        text = tpl.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RetouchPanel(
    activeTool: StudioToolId,
    editState: StudioEditState,
    onUpdateEditState: (Boolean, (StudioEditState) -> StudioEditState) -> Unit
) {
    when (activeTool) {
        StudioToolId.SKIN_SMOOTH, StudioToolId.AI_RETOUCH -> LabeledStudioSlider(
            label = "Skin Smoothness",
            value = editState.skinSmooth,
            valueRange = 0f..1f,
            displayValue = "${(editState.skinSmooth * 100).toInt()}%",
            onValueChange = { v -> onUpdateEditState(false) { it.copy(skinSmooth = v) } }
        )
        StudioToolId.FACE, StudioToolId.BRIGHTEN -> LabeledStudioSlider(
            label = "Face Radiance & Glow",
            value = editState.faceGlow,
            valueRange = 0f..1f,
            displayValue = "${(editState.faceGlow * 100).toInt()}%",
            onValueChange = { v -> onUpdateEditState(false) { it.copy(faceGlow = v) } }
        )
        StudioToolId.BODY -> LabeledStudioSlider(
            label = "Body Proportion Balance",
            value = editState.bodyBalance,
            valueRange = -0.5f..0.5f,
            displayValue = "${(editState.bodyBalance * 100).toInt()}",
            onValueChange = { v -> onUpdateEditState(false) { it.copy(bodyBalance = v) } }
        )
        StudioToolId.HAIR -> LabeledStudioSlider(
            label = "Hair Shine & Contrast",
            value = editState.hairGloss,
            valueRange = 0f..1f,
            displayValue = "${(editState.hairGloss * 100).toInt()}%",
            onValueChange = { v -> onUpdateEditState(false) { it.copy(hairGloss = v) } }
        )
        StudioToolId.MAKEUP -> LabeledStudioSlider(
            label = "Warm Lip & Cheek Tint",
            value = editState.makeupTint,
            valueRange = 0f..1f,
            displayValue = "${(editState.makeupTint * 100).toInt()}%",
            onValueChange = { v -> onUpdateEditState(false) { it.copy(makeupTint = v) } }
        )
        else -> Unit
    }
}

@Composable
private fun LabeledStudioSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(text = displayValue, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = SurfaceBright,
                activeTrackColor = SurfaceBright,
                inactiveTrackColor = SurfacePill
            )
        )
    }
}
