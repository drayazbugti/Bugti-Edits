package com.example.model

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.R

enum class HomeTab {
    HOME, TEMPLATES, CLOUD, ME
}

enum class AppThemeMode {
    WHITE, BLACK
}

enum class StudioBadge(val label: String) {
    NONE(""),
    HOT("Hot"),
    PRO("PRO"),
    NEW("New")
}

enum class StudioToolId(val label: String, val category: String, val badge: StudioBadge = StudioBadge.NONE) {
    QUALITY_ENHANCER("Quality Enhancer", "Enhance", StudioBadge.HOT),
    SMART_ENHANCE("Smart Enhance", "Enhance", StudioBadge.PRO),
    SUPER_RESOLUTION("Super Resolution", "Enhance", StudioBadge.NONE),
    AI_REMOVAL("AI Removal", "Retouch", StudioBadge.NONE),
    FACE("Face", "Retouch", StudioBadge.NONE),
    BODY("Body", "Retouch", StudioBadge.NONE),
    AI_RETOUCH("AI Retouch", "Retouch", StudioBadge.NONE),
    COLOR_MATCH("Color Match", "Color", StudioBadge.NEW),
    HAIR("Hair", "Retouch", StudioBadge.NONE),
    MAKEUP("Makeup", "Retouch", StudioBadge.NONE),
    TRENDING("Trending", "Templates", StudioBadge.NEW),
    ALL_TOOLS("All Tools(56)", "Catalog", StudioBadge.HOT),

    // Editor bottom bar tools
    EDIT("Edit", "Edit"),
    FILTERS("Filters", "Color"),
    ADJUST("Adjust", "Color"),
    ENHANCE("Enhance", "Enhance"),
    EFFECTS("Effects", "Effects"),
    TEXT("Text", "Creative"),
    MOSAIC("Mosaic", "Privacy"),
    REMOVAL("Removal", "Retouch"),
    OVERLAY("Overlay", "Effects"),
    AUDIO("Audio", "Edit"),
    STICKERS("Stickers", "Creative"),
    FRAMES("Frames", "Creative"),
    MAGNIFIER("Magnifier", "Inspect"),
    CANVAS("Canvas", "Edit"),
    TEMPLATE("Template", "Templates"),
    SKIN_SMOOTH("Skin Smooth", "Retouch"),
    BRIGHTEN("Brighten", "Retouch")
}

enum class EnhanceSegment {
    GENERAL, SCENARIO
}

enum class EnhanceMode(
    val title: String,
    val shortBadge: String,
    val description: String,
    val segment: EnhanceSegment,
    val badge: StudioBadge = StudioBadge.NONE,
    val defaultStrength: Float = 0.75f
) {
    NONE("Original", "RAW", "Original unenhanced source media", EnhanceSegment.GENERAL, StudioBadge.NONE, 0f),
    HD("HD", "HD", "Balanced clarity boost and edge sharpening", EnhanceSegment.GENERAL, StudioBadge.NONE, 0.55f),
    ULTRA_HD("Ultra HD", "HD+", "Advanced clarity — less noise, more detail", EnhanceSegment.GENERAL, StudioBadge.NONE, 0.80f),
    PORTRAIT("Portrait", "FACE", "Face lock clarity with natural skin smoothness", EnhanceSegment.GENERAL, StudioBadge.NONE, 0.72f),
    AI_UHD("AI UHD", "AI\nUHD", "Deep detail reconstruction & dynamic contrast", EnhanceSegment.GENERAL, StudioBadge.HOT, 0.92f),

    NIGHT_DENOISE("Night Denoise", "NIGHT", "Low-light grain suppression & shadow lift", EnhanceSegment.SCENARIO, StudioBadge.NONE, 0.85f),
    ANIME_4K("4K Line", "4K", "Crisp vector-like contours & vibrant color pop", EnhanceSegment.SCENARIO, StudioBadge.HOT, 0.88f),
    OLD_PHOTO("Restore", "REST", "Contrast recovery for faded & soft captures", EnhanceSegment.SCENARIO, StudioBadge.NONE, 0.78f),
    MOTION_FIX("Deblur", "FIX", "Micro-sharpening for handheld motion softness", EnhanceSegment.SCENARIO, StudioBadge.NEW, 0.82f)
}

enum class FilterPreset(val label: String, val accentHex: Long) {
    NATURAL("Natural", 0xFF8C888C),
    TOKYO_NIGHT("Tokyo Night", 0xFF4ADBC8),
    GOLDEN_HOUR("Golden Hour", 0xFFFFB25C),
    TEAL_ORANGE("Teal & Orange", 0xFFFF6B4A),
    CRISP_BW("Crisp B&W", 0xFFDADADA),
    VELVET_MATTE("Velvet Matte", 0xFFB892B8),
    CYBER_NEON("Cyber Neon", 0xFFFF5CA8),
    FUJI_WARM("Fuji Warm", 0xFFE2C275)
}

enum class EffectMode(val label: String) {
    NONE("None"),
    FILM_GRAIN("Film Grain"),
    GLOW_BLOOM("Glow Bloom"),
    SOFT_FOCUS("Soft Focus"),
    CINEMA_HAZE("Cinema Haze")
}

enum class OverlayMode(val label: String) {
    NONE("None"),
    SUNSET_LEAK("Sunset Leak"),
    PRISM_FLARE("Prism Flare"),
    STUDIO_DUO("Studio Duo")
}

enum class AspectRatioOption(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    SQUARE_1_1("1:1", 1f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    REEL_9_16("9:16", 9f / 16f),
    CINEMA_16_9("16:9", 16f / 9f)
}

enum class FrameStyle(val label: String) {
    NONE("Clean"),
    CINEMA_BARS("Cinema 2.39"),
    POLAROID("Polaroid"),
    DARK_FILM("Film Border")
}

enum class ExportResolution(val label: String, val maxDim: Int) {
    HD_1080("1080p HD", 1920),
    UHD_4K("4K Ultra HD", 3840)
}

enum class EditorMainMode {
    RETOUCH, EDIT
}

@Immutable
data class TextOverlayItem(
    val id: String,
    val text: String,
    val colorArgb: Long = 0xFFFFFFFF,
    val normX: Float = 0.5f,
    val normY: Float = 0.25f,
    val scale: Float = 1f
)

@Immutable
data class StickerOverlayItem(
    val id: String,
    val label: String,
    val normX: Float = 0.5f,
    val normY: Float = 0.5f,
    val scale: Float = 1f
)

@Immutable
data class NormPoint(
    val x: Float,
    val y: Float
)

@Immutable
data class StudioEditState(
    val enhanceMode: EnhanceMode = EnhanceMode.NONE,
    val enhanceStrength: Float = 0.75f,
    val filterPreset: FilterPreset = FilterPreset.NATURAL,
    val filterIntensity: Float = 0.75f,
    val brightness: Float = 0f,     // -0.5 .. +0.5
    val contrast: Float = 0f,       // -0.5 .. +0.5
    val saturation: Float = 0f,     // -0.5 .. +0.5
    val sharpen: Float = 0f,        // 0 .. 1
    val warmth: Float = 0f,         // -0.5 .. +0.5
    val vignette: Float = 0f,       // 0 .. 1
    // Retouch parameters
    val skinSmooth: Float = 0f,     // 0 .. 1
    val faceGlow: Float = 0f,       // 0 .. 1
    val bodyBalance: Float = 0f,    // -0.2 .. +0.2 subtle aspect slimming
    val hairGloss: Float = 0f,      // 0 .. 1
    val makeupTint: Float = 0f,     // 0 .. 1
    // Effects & Overlays
    val effectMode: EffectMode = EffectMode.NONE,
    val effectIntensity: Float = 0.5f,
    val overlayMode: OverlayMode = OverlayMode.NONE,
    val overlayIntensity: Float = 0.45f,
    // Geometry / Video Edit
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val aspectRatio: AspectRatioOption = AspectRatioOption.ORIGINAL,
    val frameStyle: FrameStyle = FrameStyle.NONE,
    val playbackSpeed: Float = 1f,
    val muteAudio: Boolean = false,
    val audioVolume: Float = 1f,
    val trimStartFraction: Float = 0f,
    val trimEndFraction: Float = 1f,
    // Creative / Privacy / Removal
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val stickerOverlays: List<StickerOverlayItem> = emptyList(),
    val mosaicPoints: List<NormPoint> = emptyList(),
    val removalPoints: List<NormPoint> = emptyList(),
    val exportResolution: ExportResolution = ExportResolution.HD_1080
) {
    val hasAnyEdits: Boolean
        get() = enhanceMode != EnhanceMode.NONE ||
            filterPreset != FilterPreset.NATURAL ||
            brightness != 0f || contrast != 0f || saturation != 0f ||
            sharpen != 0f || warmth != 0f || vignette != 0f ||
            skinSmooth != 0f || faceGlow != 0f || bodyBalance != 0f ||
            hairGloss != 0f || makeupTint != 0f ||
            effectMode != EffectMode.NONE || overlayMode != OverlayMode.NONE ||
            rotationDegrees != 0 || flipHorizontal ||
            aspectRatio != AspectRatioOption.ORIGINAL || frameStyle != FrameStyle.NONE ||
            playbackSpeed != 1f || muteAudio ||
            trimStartFraction > 0f || trimEndFraction < 1f ||
            textOverlays.isNotEmpty() || stickerOverlays.isNotEmpty() ||
            mosaicPoints.isNotEmpty() || removalPoints.isNotEmpty()
}

sealed interface MediaSource {
    data class ContentUri(val uri: Uri, val isVideo: Boolean) : MediaSource
    data class SampleDrawable(@DrawableRes val resId: Int, val title: String) : MediaSource
}

@Immutable
data class StudioTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val tag: String,
    @DrawableRes val previewRes: Int,
    val editState: StudioEditState
)

@Immutable
data class SavedCreation(
    val id: String,
    val title: String,
    val uriString: String,
    val isVideo: Boolean,
    val resolutionLabel: String,
    val modeLabel: String,
    val createdAtMillis: Long,
    val isUploadedToCloud: Boolean = false,
    @DrawableRes val sampleResId: Int? = null
)

@Immutable
data class StudioPreferences(
    val themeMode: AppThemeMode = AppThemeMode.WHITE,
    val autoWhiteOnClose: Boolean = false,
    val autoSaveDrafts: Boolean = true,
    val cloudAutoBackup: Boolean = false,
    val credits: Int = 25,
    val defaultResolution: ExportResolution = ExportResolution.UHD_4K,
    val autoFaceLock: Boolean = true,
    val showBeforeAfterHint: Boolean = true,
    val isLoggedIn: Boolean = false,
    val loggedInEmail: String = "",
    val profileName: String = "",
    val profilePhotoPath: String = ""
)

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data class Processing(val progress: Float, val message: String) : ExportStatus
    data class Success(val outputUri: Uri, val isVideo: Boolean, val summary: String) : ExportStatus
    data class Error(val message: String) : ExportStatus
}

object StudioCatalog {
    val homeGridTools = listOf(
        StudioToolId.QUALITY_ENHANCER,
        StudioToolId.SMART_ENHANCE,
        StudioToolId.SUPER_RESOLUTION,
        StudioToolId.AI_REMOVAL,
        StudioToolId.FACE,
        StudioToolId.BODY,
        StudioToolId.AI_RETOUCH,
        StudioToolId.COLOR_MATCH,
        StudioToolId.HAIR,
        StudioToolId.MAKEUP,
        StudioToolId.TRENDING,
        StudioToolId.ALL_TOOLS
    )

    val editorEditTools = listOf(
        StudioToolId.EDIT,
        StudioToolId.FILTERS,
        StudioToolId.ADJUST,
        StudioToolId.ENHANCE,
        StudioToolId.EFFECTS,
        StudioToolId.TEXT,
        StudioToolId.MOSAIC,
        StudioToolId.REMOVAL,
        StudioToolId.OVERLAY,
        StudioToolId.AUDIO,
        StudioToolId.STICKERS,
        StudioToolId.FRAMES,
        StudioToolId.MAGNIFIER,
        StudioToolId.CANVAS,
        StudioToolId.TEMPLATE
    )

    val editorRetouchTools = listOf(
        StudioToolId.FACE,
        StudioToolId.SKIN_SMOOTH,
        StudioToolId.BRIGHTEN,
        StudioToolId.AI_RETOUCH,
        StudioToolId.BODY,
        StudioToolId.HAIR,
        StudioToolId.MAKEUP,
        StudioToolId.COLOR_MATCH
    )

    val allSearchableTools = listOf(
        StudioToolId.QUALITY_ENHANCER,
        StudioToolId.SMART_ENHANCE,
        StudioToolId.SUPER_RESOLUTION,
        StudioToolId.AI_REMOVAL,
        StudioToolId.FACE,
        StudioToolId.BODY,
        StudioToolId.AI_RETOUCH,
        StudioToolId.COLOR_MATCH,
        StudioToolId.HAIR,
        StudioToolId.MAKEUP,
        StudioToolId.FILTERS,
        StudioToolId.ADJUST,
        StudioToolId.EFFECTS,
        StudioToolId.TEXT,
        StudioToolId.MOSAIC,
        StudioToolId.OVERLAY,
        StudioToolId.AUDIO,
        StudioToolId.STICKERS,
        StudioToolId.FRAMES,
        StudioToolId.MAGNIFIER,
        StudioToolId.CANVAS
    )

    val templates = listOf(
        StudioTemplate(
            id = "golden_skyline",
            title = "Golden Skyline 4K",
            subtitle = "Crisp architectural Ultra HD + warm sunlight lift",
            tag = "Hot",
            previewRes = R.drawable.img_demo_skyline,
            editState = StudioEditState(
                enhanceMode = EnhanceMode.ULTRA_HD,
                enhanceStrength = 0.85f,
                filterPreset = FilterPreset.GOLDEN_HOUR,
                filterIntensity = 0.65f,
                contrast = 0.12f,
                sharpen = 0.45f
            )
        ),
        StudioTemplate(
            id = "portrait_studio",
            title = "Velvet Portrait Glow",
            subtitle = "Face-locked clarity with soft skin & golden warmth",
            tag = "PRO",
            previewRes = R.drawable.img_demo_portrait,
            editState = StudioEditState(
                enhanceMode = EnhanceMode.PORTRAIT,
                enhanceStrength = 0.78f,
                skinSmooth = 0.48f,
                faceGlow = 0.35f,
                warmth = 0.12f,
                vignette = 0.18f
            )
        ),
        StudioTemplate(
            id = "neon_rain",
            title = "Tokyo Night UHD",
            subtitle = "Night denoise + neon cyan shadows & cinema bars",
            tag = "New",
            previewRes = R.drawable.img_showcase_cinema,
            editState = StudioEditState(
                enhanceMode = EnhanceMode.NIGHT_DENOISE,
                enhanceStrength = 0.88f,
                filterPreset = FilterPreset.TOKYO_NIGHT,
                filterIntensity = 0.80f,
                contrast = 0.18f,
                frameStyle = FrameStyle.CINEMA_BARS
            )
        ),
        StudioTemplate(
            id = "desert_film",
            title = "Desert Teal & Amber",
            subtitle = "Analog 35mm cinema grade with subtle film grain",
            tag = "Trending",
            previewRes = R.drawable.img_template_film,
            editState = StudioEditState(
                enhanceMode = EnhanceMode.AI_UHD,
                enhanceStrength = 0.75f,
                filterPreset = FilterPreset.TEAL_ORANGE,
                filterIntensity = 0.78f,
                effectMode = EffectMode.FILM_GRAIN,
                effectIntensity = 0.35f,
                aspectRatio = AspectRatioOption.CINEMA_16_9
            )
        )
    )

    val stickerChoices = listOf(
        "✦ 4K UHD",
        "HD+",
        "STUDIO",
        "★ PRO",
        "⚡ BOOST",
        "♥ GLOW"
    )

    val textColorChoices = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFF6B4A),
        Color(0xFFE2C275),
        Color(0xFF4ADBC8),
        Color(0xFFFF5CA8),
        Color(0xFF121112)
    )
}
