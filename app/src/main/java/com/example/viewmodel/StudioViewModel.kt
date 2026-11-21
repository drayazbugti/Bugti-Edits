package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.transformer.Transformer
import com.example.R
import com.example.data.StudioRepository
import com.example.media.ImageEnhancerEngine
import com.example.media.VideoEnhancerEngine
import com.example.model.AppThemeMode
import com.example.model.AspectRatioOption
import com.example.model.EditorMainMode
import com.example.model.EffectMode
import com.example.model.EnhanceMode
import com.example.model.EnhanceSegment
import com.example.model.ExportResolution
import com.example.model.ExportStatus
import com.example.model.FilterPreset
import com.example.model.FrameStyle
import com.example.model.HomeTab
import com.example.model.MediaSource
import com.example.model.NormPoint
import com.example.model.OverlayMode
import com.example.model.SavedCreation
import com.example.model.StickerOverlayItem
import com.example.model.StudioCatalog
import com.example.model.StudioEditState
import com.example.model.StudioPreferences
import com.example.model.StudioTemplate
import com.example.model.StudioToolId
import com.example.model.TextOverlayItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@Immutable
data class StudioUiState(
    val currentHomeTab: HomeTab = HomeTab.HOME,
    val searchQuery: String = "",
    val qualitySegment: EnhanceSegment = EnhanceSegment.GENERAL,
    val selectedQualityMode: EnhanceMode = EnhanceMode.ULTRA_HD,
    val demoSplitPosition: Float = 0.52f,
    val activeMediaSource: MediaSource? = null,
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val isProcessingPreview: Boolean = false,
    val videoDurationMs: Long = 19_000L,
    val videoFrames: List<Bitmap> = emptyList(),
    val editState: StudioEditState = StudioEditState(),
    val canUndo: Boolean = false,
    val editorMainMode: EditorMainMode = EditorMainMode.EDIT,
    val selectedEditorTool: StudioToolId? = StudioToolId.ENHANCE,
    val isHoldingCompare: Boolean = false,
    val isMagnifierEnabled: Boolean = false,
    val selectedCoverFrameIndex: Int = 0,
    val exportStatus: ExportStatus = ExportStatus.Idle,
    val pendingToolAfterPick: StudioToolId? = null,
    val pendingModeAfterPick: EditorMainMode = EditorMainMode.EDIT,
    val pendingPresetEditState: StudioEditState? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudioRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val savedCreations: StateFlow<List<SavedCreation>> = repository.savedCreationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preferences: StateFlow<StudioPreferences> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudioPreferences())

    private val undoStack = ArrayDeque<StudioEditState>()
    private var previewJob: Job? = null
    private var activeTransformer: Transformer? = null

    init {
        viewModelScope.launch {
            repository.resetToWhiteThemeIfConfigured()
        }
    }

    fun selectHomeTab(tab: HomeTab) {
        _uiState.update { it.copy(currentHomeTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectQualitySegment(segment: EnhanceSegment) {
        val defaultMode = if (segment == EnhanceSegment.GENERAL) {
            EnhanceMode.ULTRA_HD
        } else {
            EnhanceMode.NIGHT_DENOISE
        }
        _uiState.update {
            it.copy(qualitySegment = segment, selectedQualityMode = defaultMode)
        }
    }

    fun selectQualityMode(mode: EnhanceMode) {
        _uiState.update { it.copy(selectedQualityMode = mode) }
    }

    fun updateDemoSplitPosition(fraction: Float) {
        _uiState.update { it.copy(demoSplitPosition = fraction.coerceIn(0.08f, 0.92f)) }
    }

    fun preparePickerContext(
        mainMode: EditorMainMode = EditorMainMode.EDIT,
        initialTool: StudioToolId? = StudioToolId.ENHANCE,
        presetState: StudioEditState? = null
    ) {
        _uiState.update {
            it.copy(
                pendingModeAfterPick = mainMode,
                pendingToolAfterPick = initialTool,
                pendingPresetEditState = presetState
            )
        }
    }

    fun openMediaUri(uri: Uri, isVideo: Boolean) {
        val source = MediaSource.ContentUri(uri, isVideo)
        openMediaSource(source)
    }

    fun openSampleDrawable(@DrawableRes resId: Int, title: String) {
        openMediaSource(MediaSource.SampleDrawable(resId, title))
    }

    fun applyTemplateAndOpenSample(template: StudioTemplate) {
        preparePickerContext(
            mainMode = EditorMainMode.EDIT,
            initialTool = StudioToolId.ENHANCE,
            presetState = template.editState
        )
        openSampleDrawable(template.previewRes, template.title)
    }

    fun applyTemplateInEditor(template: StudioTemplate) {
        pushUndoSnapshot()
        _uiState.update { state ->
            state.copy(
                editState = template.editState.copy(
                    rotationDegrees = state.editState.rotationDegrees,
                    flipHorizontal = state.editState.flipHorizontal,
                    exportResolution = state.editState.exportResolution
                ),
                canUndo = undoStack.isNotEmpty()
            )
        }
        schedulePreviewReprocess()
    }

    private fun openMediaSource(source: MediaSource) {
        val current = _uiState.value
        val defaultRes = preferences.value.defaultResolution
        val initialEditState = (current.pendingPresetEditState ?: StudioEditState()).copy(
            exportResolution = defaultRes
        )
        val mainMode = current.pendingModeAfterPick
        val initialTool = current.pendingToolAfterPick ?: if (mainMode == EditorMainMode.RETOUCH) {
            StudioToolId.FACE
        } else {
            StudioToolId.ENHANCE
        }

        undoStack.clear()
        _uiState.update {
            it.copy(
                activeMediaSource = source,
                originalBitmap = null,
                processedBitmap = null,
                videoFrames = emptyList(),
                videoDurationMs = 19_000L,
                editState = initialEditState,
                canUndo = false,
                editorMainMode = mainMode,
                selectedEditorTool = initialTool,
                isProcessingPreview = true,
                isMagnifierEnabled = false,
                selectedCoverFrameIndex = 0,
                exportStatus = ExportStatus.Idle,
                pendingPresetEditState = null
            )
        }

        val ctx = getApplication<Application>().applicationContext
        viewModelScope.launch {
            if (source is MediaSource.ContentUri && source.isVideo) {
                val (durationMs, frames) = VideoEnhancerEngine.extractVideoInfoAndThumbnails(ctx, source.uri)
                _uiState.update {
                    it.copy(
                        videoDurationMs = durationMs,
                        videoFrames = frames,
                        isProcessingPreview = false
                    )
                }
            } else {
                val loaded = ImageEnhancerEngine.loadSourceBitmap(ctx, source, maxDimension = 1280)
                if (loaded != null) {
                    val processed = ImageEnhancerEngine.processBitmap(loaded, initialEditState, isExport = false)
                    _uiState.update {
                        it.copy(
                            originalBitmap = loaded,
                            processedBitmap = processed,
                            isProcessingPreview = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isProcessingPreview = false) }
                }
            }
        }
    }

    fun switchEditorMainMode(mode: EditorMainMode) {
        val defaultTool = if (mode == EditorMainMode.RETOUCH) {
            StudioToolId.FACE
        } else {
            StudioToolId.ENHANCE
        }
        _uiState.update {
            it.copy(
                editorMainMode = mode,
                selectedEditorTool = defaultTool,
                isMagnifierEnabled = false
            )
        }
    }

    fun selectEditorTool(tool: StudioToolId) {
        if (tool == StudioToolId.MAGNIFIER) {
            _uiState.update {
                val nextMag = !it.isMagnifierEnabled
                it.copy(
                    selectedEditorTool = if (nextMag) StudioToolId.MAGNIFIER else null,
                    isMagnifierEnabled = nextMag
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedEditorTool = if (it.selectedEditorTool == tool) null else tool,
                isMagnifierEnabled = false
            )
        }
    }

    fun setHoldingCompare(holding: Boolean) {
        _uiState.update { it.copy(isHoldingCompare = holding) }
    }

    fun selectCoverFrame(index: Int) {
        _uiState.update { it.copy(selectedCoverFrameIndex = index) }
    }

    private fun pushUndoSnapshot() {
        val current = _uiState.value.editState
        if (undoStack.size >= 25) undoStack.removeFirst()
        undoStack.addLast(current)
    }

    fun undoLastEdit() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        _uiState.update {
            it.copy(
                editState = prev,
                canUndo = undoStack.isNotEmpty()
            )
        }
        schedulePreviewReprocess()
    }

    fun resetAllEdits() {
        pushUndoSnapshot()
        _uiState.update {
            it.copy(
                editState = StudioEditState(exportResolution = it.editState.exportResolution),
                canUndo = undoStack.isNotEmpty()
            )
        }
        schedulePreviewReprocess()
    }

    fun updateEditState(recordUndo: Boolean = true, transform: (StudioEditState) -> StudioEditState) {
        if (recordUndo) pushUndoSnapshot()
        _uiState.update {
            it.copy(
                editState = transform(it.editState),
                canUndo = undoStack.isNotEmpty()
            )
        }
        schedulePreviewReprocess()
    }

    fun setEnhanceMode(mode: EnhanceMode) {
        updateEditState {
            it.copy(
                enhanceMode = mode,
                enhanceStrength = if (mode == EnhanceMode.NONE) 0f else mode.defaultStrength
            )
        }
    }

    fun setFilterPreset(preset: FilterPreset) {
        updateEditState { it.copy(filterPreset = preset) }
    }

    fun setEffectMode(mode: EffectMode) {
        updateEditState { it.copy(effectMode = mode) }
    }

    fun setOverlayMode(mode: OverlayMode) {
        updateEditState { it.copy(overlayMode = mode) }
    }

    fun setAspectRatio(option: AspectRatioOption) {
        updateEditState { it.copy(aspectRatio = option) }
    }

    fun setFrameStyle(style: FrameStyle) {
        updateEditState { it.copy(frameStyle = style) }
    }

    fun setExportResolution(resolution: ExportResolution) {
        updateEditState(recordUndo = false) { it.copy(exportResolution = resolution) }
    }

    fun rotate90() {
        updateEditState { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    }

    fun toggleFlipHorizontal() {
        updateEditState { it.copy(flipHorizontal = !it.flipHorizontal) }
    }

    fun addTextOverlay(text: String, colorArgb: Long) {
        if (text.isBlank()) return
        updateEditState {
            it.copy(
                textOverlays = it.textOverlays + TextOverlayItem(
                    id = UUID.randomUUID().toString(),
                    text = text.trim(),
                    colorArgb = colorArgb
                )
            )
        }
    }

    fun moveTextOverlay(id: String, normX: Float, normY: Float) {
        _uiState.update { state ->
            state.copy(
                editState = state.editState.copy(
                    textOverlays = state.editState.textOverlays.map { item ->
                        if (item.id == id) {
                            item.copy(
                                normX = normX.coerceIn(0.08f, 0.92f),
                                normY = normY.coerceIn(0.08f, 0.92f)
                            )
                        } else item
                    }
                )
            )
        }
    }

    fun removeTextOverlay(id: String) {
        updateEditState { state ->
            state.copy(textOverlays = state.textOverlays.filterNot { it.id == id })
        }
    }

    fun addStickerOverlay(label: String) {
        updateEditState {
            it.copy(
                stickerOverlays = it.stickerOverlays + StickerOverlayItem(
                    id = UUID.randomUUID().toString(),
                    label = label
                )
            )
        }
    }

    fun moveStickerOverlay(id: String, normX: Float, normY: Float) {
        _uiState.update { state ->
            state.copy(
                editState = state.editState.copy(
                    stickerOverlays = state.editState.stickerOverlays.map { item ->
                        if (item.id == id) {
                            item.copy(
                                normX = normX.coerceIn(0.08f, 0.92f),
                                normY = normY.coerceIn(0.08f, 0.92f)
                            )
                        } else item
                    }
                )
            )
        }
    }

    fun removeStickerOverlay(id: String) {
        updateEditState { state ->
            state.copy(stickerOverlays = state.stickerOverlays.filterNot { it.id == id })
        }
    }

    fun addMosaicPoint(normX: Float, normY: Float) {
        updateEditState {
            it.copy(mosaicPoints = it.mosaicPoints + NormPoint(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f)))
        }
    }

    fun clearMosaicPoints() {
        updateEditState { it.copy(mosaicPoints = emptyList()) }
    }

    fun addRemovalPoint(normX: Float, normY: Float) {
        updateEditState {
            it.copy(removalPoints = it.removalPoints + NormPoint(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f)))
        }
    }

    fun clearRemovalPoints() {
        updateEditState { it.copy(removalPoints = emptyList()) }
    }

    private fun schedulePreviewReprocess() {
        val original = _uiState.value.originalBitmap ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(45L)
            _uiState.update { it.copy(isProcessingPreview = true) }
            val stateSnapshot = _uiState.value.editState
            val result = ImageEnhancerEngine.processBitmap(original, stateSnapshot, isExport = false)
            _uiState.update {
                it.copy(
                    processedBitmap = result,
                    isProcessingPreview = false
                )
            }
        }
    }

    fun exportCurrentCreation() {
        val state = _uiState.value
        val source = state.activeMediaSource ?: return
        val ctx = getApplication<Application>().applicationContext

        if (source is MediaSource.ContentUri && source.isVideo) {
            _uiState.update {
                it.copy(exportStatus = ExportStatus.Processing(0.05f, "Rendering enhanced video..."))
            }
            activeTransformer = VideoEnhancerEngine.exportEditedVideo(
                context = ctx,
                sourceUri = source.uri,
                durationMs = state.videoDurationMs,
                editState = state.editState,
                onProgress = { progress ->
                    _uiState.update {
                        it.copy(
                            exportStatus = ExportStatus.Processing(
                                progress = progress.coerceAtLeast(0.08f),
                                message = "Encoding ${state.editState.exportResolution.label}..."
                            )
                        )
                    }
                },
                onComplete = { result ->
                    activeTransformer = null
                    result.fold(
                        onSuccess = { outUri ->
                            recordExportCreation(
                                title = "Video ${state.editState.enhanceMode.title}",
                                uri = outUri,
                                isVideo = true,
                                editState = state.editState
                            )
                            _uiState.update {
                                it.copy(
                                    exportStatus = ExportStatus.Success(
                                        outputUri = outUri,
                                        isVideo = true,
                                        summary = "Saved ${state.editState.exportResolution.label} MP4 to Movies/Bugti Editz"
                                    )
                                )
                            }
                        },
                        onFailure = { err ->
                            _uiState.update {
                                it.copy(
                                    exportStatus = ExportStatus.Error(
                                        err.localizedMessage ?: "Video export failed"
                                    )
                                )
                            }
                        }
                    )
                }
            )
        } else {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(exportStatus = ExportStatus.Processing(0.25f, "Upscaling & sharpening..."))
                }
                val highRes = ImageEnhancerEngine.loadSourceBitmap(
                    ctx,
                    source,
                    maxDimension = state.editState.exportResolution.maxDim
                )
                if (highRes == null) {
                    _uiState.update {
                        it.copy(exportStatus = ExportStatus.Error("Could not read source photo"))
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(exportStatus = ExportStatus.Processing(0.65f, "Applying color & retouch pass..."))
                }
                val rendered = ImageEnhancerEngine.processBitmap(highRes, state.editState, isExport = true)
                _uiState.update {
                    it.copy(exportStatus = ExportStatus.Processing(0.90f, "Saving to Gallery..."))
                }
                val labelTitle = when (source) {
                    is MediaSource.SampleDrawable -> source.title
                    is MediaSource.ContentUri -> "Bugti Photo"
                }
                val saveRes = ImageEnhancerEngine.saveBitmapToGallery(ctx, rendered, "BugtiEditz")
                saveRes.fold(
                    onSuccess = { outUri ->
                        recordExportCreation(
                            title = "$labelTitle (${state.editState.enhanceMode.shortBadge.replace("\n", " ")})",
                            uri = outUri,
                            isVideo = false,
                            editState = state.editState
                        )
                        _uiState.update {
                            it.copy(
                                exportStatus = ExportStatus.Success(
                                    outputUri = outUri,
                                    isVideo = false,
                                    summary = "Saved ${rendered.width}x${rendered.height} to Pictures/Bugti Editz"
                                )
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                exportStatus = ExportStatus.Error(
                                    err.localizedMessage ?: "Photo export failed"
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    fun cancelExport() {
        runCatching { activeTransformer?.cancel() }
        activeTransformer = null
        _uiState.update { it.copy(exportStatus = ExportStatus.Idle) }
    }

    fun dismissExportStatus() {
        _uiState.update { it.copy(exportStatus = ExportStatus.Idle) }
    }

    private fun recordExportCreation(
        title: String,
        uri: Uri,
        isVideo: Boolean,
        editState: StudioEditState
    ) {
        viewModelScope.launch {
            val modeText = if (editState.enhanceMode != EnhanceMode.NONE) {
                editState.enhanceMode.title
            } else if (editState.filterPreset != FilterPreset.NATURAL) {
                editState.filterPreset.label
            } else {
                "Studio Edit"
            }
            repository.addSavedCreation(
                SavedCreation(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    uriString = uri.toString(),
                    isVideo = isVideo,
                    resolutionLabel = editState.exportResolution.label,
                    modeLabel = modeText,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeSavedCreation(id: String) {
        viewModelScope.launch { repository.removeCreation(id) }
    }

    fun removeSelectedCreations(ids: Set<String>) {
        viewModelScope.launch { repository.removeCreations(ids) }
    }

    fun renameCreation(id: String, newTitle: String) {
        viewModelScope.launch { repository.renameCreation(id, newTitle) }
    }

    fun duplicateCreation(id: String) {
        viewModelScope.launch {
            repository.duplicateCreation(id, UUID.randomUUID().toString())
        }
    }

    fun markCreationsUploadedToCloud(ids: Set<String>) {
        viewModelScope.launch { repository.markUploadedToCloud(ids) }
    }

    fun createQuickDraft() {
        viewModelScope.launch {
            val count = savedCreations.value.size + 1
            val samples = listOf(
                R.drawable.img_demo_skyline,
                R.drawable.img_showcase_cinema,
                R.drawable.img_demo_portrait,
                R.drawable.img_template_film
            )
            val chosenRes = samples[count % samples.size]
            val currentPrefs = preferences.value
            repository.addSavedCreation(
                SavedCreation(
                    id = UUID.randomUUID().toString(),
                    title = "Studio Draft #$count",
                    uriString = "",
                    isVideo = false,
                    resolutionLabel = currentPrefs.defaultResolution.label,
                    modeLabel = "Ultra HD",
                    createdAtMillis = System.currentTimeMillis(),
                    isUploadedToCloud = false,
                    sampleResId = chosenRes
                )
            )
        }
    }

    fun clearSavedHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun updatePreferences(transform: (StudioPreferences) -> StudioPreferences) {
        viewModelScope.launch {
            repository.updatePreferences(transform(preferences.value))
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun onAuthUserObserved(user: com.google.firebase.auth.FirebaseUser?) {
        if (user != null) {
            viewModelScope.launch {
                repository.setAuthSession(
                    isLoggedIn = true,
                    email = user.email,
                    defaultDisplayName = user.displayName
                )
            }
        }
    }

    fun onUserSignedOut() {
        viewModelScope.launch {
            repository.setAuthSession(
                isLoggedIn = false,
                email = null,
                defaultDisplayName = null
            )
        }
    }

    fun saveProfileName(name: String) {
        viewModelScope.launch {
            repository.saveProfileName(name)
        }
    }

    fun saveProfilePhoto(uri: Uri, onCompleted: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val savedPath = repository.saveProfilePhotoFromUri(uri)
            onCompleted(savedPath != null)
        }
    }

    fun clearProfilePhoto() {
        viewModelScope.launch {
            repository.clearProfilePhoto()
        }
    }

    fun claimBonusCredits(amount: Int = 10) {
        updatePreferences { it.copy(credits = it.credits + amount) }
    }

    fun onAppStopped() {
        if (preferences.value.autoWhiteOnClose && preferences.value.themeMode != AppThemeMode.WHITE) {
            viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
                repository.resetToWhiteThemeIfConfigured()
            }
        }
    }
}
