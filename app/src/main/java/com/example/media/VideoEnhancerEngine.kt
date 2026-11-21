package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.model.EnhanceMode
import com.example.model.ExportResolution
import com.example.model.FilterPreset
import com.example.model.StudioEditState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.math.max

object VideoEnhancerEngine {

    suspend fun extractVideoInfoAndThumbnails(
        context: Context,
        uri: Uri,
        frameCount: Int = 8
    ): Pair<Long, List<Bitmap>> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull()?.coerceAtLeast(1000L) ?: 10_000L
            val frames = mutableListOf<Bitmap>()
            for (i in 0 until frameCount) {
                val timeUs = (durationMs * 1000L * i) / max(1, frameCount)
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, 140, 140, true)
                    if (scaled != frame) frame.recycle()
                    frames.add(scaled)
                }
            }
            durationMs to frames
        } catch (_: Exception) {
            10_000L to emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }

    @OptIn(UnstableApi::class)
    fun buildMedia3Effects(editState: StudioEditState, isExport: Boolean = false): List<Effect> {
        val effects = mutableListOf<Effect>()

        // Contrast & Brightness from Enhance + Adjust + Retouch
        val enhContrast = when (editState.enhanceMode) {
            EnhanceMode.NONE -> 0f
            EnhanceMode.HD -> 0.14f * editState.enhanceStrength
            EnhanceMode.ULTRA_HD -> 0.24f * editState.enhanceStrength
            EnhanceMode.PORTRAIT -> 0.12f * editState.enhanceStrength
            EnhanceMode.AI_UHD -> 0.30f * editState.enhanceStrength
            EnhanceMode.NIGHT_DENOISE -> 0.18f * editState.enhanceStrength
            EnhanceMode.ANIME_4K -> 0.28f * editState.enhanceStrength
            EnhanceMode.OLD_PHOTO -> 0.25f * editState.enhanceStrength
            EnhanceMode.MOTION_FIX -> 0.20f * editState.enhanceStrength
        }
        val totalContrast = (editState.contrast + enhContrast + editState.hairGloss * 0.15f).coerceIn(-0.85f, 0.85f)
        if (kotlin.math.abs(totalContrast) > 0.01f) {
            effects.add(Contrast(totalContrast))
        }

        val enhBrightness = if (editState.enhanceMode == EnhanceMode.NIGHT_DENOISE) {
            0.12f * editState.enhanceStrength
        } else {
            0f
        }
        val totalBrightness = (editState.brightness + enhBrightness + editState.faceGlow * 0.12f).coerceIn(-0.8f, 0.8f)
        if (kotlin.math.abs(totalBrightness) > 0.01f) {
            effects.add(Brightness(totalBrightness))
        }

        // Saturation & Filter presets via HslAdjustment + RgbAdjustment
        val enhSat = when (editState.enhanceMode) {
            EnhanceMode.ULTRA_HD, EnhanceMode.AI_UHD -> 16f * editState.enhanceStrength
            EnhanceMode.ANIME_4K -> 24f * editState.enhanceStrength
            EnhanceMode.HD -> 8f * editState.enhanceStrength
            else -> 0f
        }
        val satDelta = (editState.saturation * 100f + enhSat + editState.makeupTint * 18f).coerceIn(-100f, 100f)
        if (editState.filterPreset == FilterPreset.CRISP_BW) {
            val bwSat = -100f * editState.filterIntensity
            effects.add(HslAdjustment.Builder().adjustSaturation(bwSat).build())
        } else if (kotlin.math.abs(satDelta) > 1f) {
            effects.add(HslAdjustment.Builder().adjustSaturation(satDelta).build())
        }

        // Warmth & Color Match preset RGB balance
        var redScale = 1f + editState.warmth * 0.30f
        var greenScale = 1f
        var blueScale = 1f - editState.warmth * 0.30f
        val fi = editState.filterIntensity
        when (editState.filterPreset) {
            FilterPreset.TOKYO_NIGHT -> {
                redScale -= 0.06f * fi
                blueScale += 0.18f * fi
            }
            FilterPreset.GOLDEN_HOUR -> {
                redScale += 0.16f * fi
                greenScale += 0.05f * fi
                blueScale -= 0.14f * fi
            }
            FilterPreset.TEAL_ORANGE -> {
                redScale += 0.14f * fi
                greenScale += 0.04f * fi
                blueScale += 0.08f * fi
            }
            FilterPreset.CYBER_NEON -> {
                redScale += 0.12f * fi
                blueScale += 0.20f * fi
            }
            FilterPreset.FUJI_WARM -> {
                redScale += 0.09f * fi
                greenScale += 0.06f * fi
            }
            FilterPreset.VELVET_MATTE -> {
                redScale += 0.04f * fi
                blueScale += 0.06f * fi
            }
            else -> Unit
        }
        if (kotlin.math.abs(redScale - 1f) > 0.01f ||
            kotlin.math.abs(greenScale - 1f) > 0.01f ||
            kotlin.math.abs(blueScale - 1f) > 0.01f
        ) {
            effects.add(
                RgbAdjustment.Builder()
                    .setRedScale(redScale.coerceIn(0.4f, 1.8f))
                    .setGreenScale(greenScale.coerceIn(0.4f, 1.8f))
                    .setBlueScale(blueScale.coerceIn(0.4f, 1.8f))
                    .build()
            )
        }

        // Rotation / Flip
        if (editState.rotationDegrees != 0 || editState.flipHorizontal) {
            val scaleX = if (editState.flipHorizontal) -1f else 1f
            effects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(editState.rotationDegrees.toFloat())
                    .setScale(scaleX, 1f)
                    .build()
            )
        }

        // Export Resolution Presentation
        if (isExport) {
            val height = if (editState.exportResolution == ExportResolution.UHD_4K) 2160 else 1080
            effects.add(Presentation.createForHeight(height))
        }

        return effects
    }

    @OptIn(UnstableApi::class)
    fun exportEditedVideo(
        context: Context,
        sourceUri: Uri,
        durationMs: Long,
        editState: StudioEditState,
        onProgress: (Float) -> Unit,
        onComplete: (Result<Uri>) -> Unit
    ): Transformer {
        val startMs = (durationMs * editState.trimStartFraction).toLong().coerceAtLeast(0L)
        val endMs = (durationMs * editState.trimEndFraction).toLong().coerceAtLeast(startMs + 500L)

        val mediaItemBuilder = MediaItem.Builder().setUri(sourceUri)
        if (editState.trimStartFraction > 0.01f || editState.trimEndFraction < 0.99f) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
        }

        val videoEffects = buildMedia3Effects(editState, isExport = true)
        val editedMediaItem = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveAudio(editState.muteAudio)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val tempOutFile = File(context.cacheDir, "bugti_export_${System.currentTimeMillis()}.mp4")
        if (tempOutFile.exists()) tempOutFile.delete()

        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()
        var isFinished = false

        lateinit var transformer: Transformer
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                isFinished = true
                val galleryResult = saveVideoFileToGallery(context, tempOutFile, "BugtiEditz_Video")
                runCatching { tempOutFile.delete() }
                onComplete(galleryResult)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                isFinished = true
                runCatching { tempOutFile.delete() }
                onComplete(Result.failure(exportException))
            }
        }

        transformer = Transformer.Builder(context)
            .addListener(listener)
            .build()

        transformer.start(editedMediaItem, tempOutFile.absolutePath)

        val progressPoll = object : Runnable {
            override fun run() {
                if (isFinished) return
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress((progressHolder.progress / 100f).coerceIn(0f, 0.99f))
                }
                if (!isFinished) {
                    handler.postDelayed(this, 200L)
                }
            }
        }
        handler.post(progressPoll)

        return transformer
    }

    private fun saveVideoFileToGallery(
        context: Context,
        sourceFile: File,
        prefix: String
    ): Result<Uri> = runCatching {
        val fileName = "${prefix}_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Bugti Editz")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create MediaStore video entry")
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: error("Unable to open video output stream")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: context.filesDir
            val outDir = File(moviesDir, "Bugti Editz").apply { mkdirs() }
            val dest = File(outDir, fileName)
            sourceFile.copyTo(dest, overwrite = true)
            Uri.fromFile(dest)
        }
    }
}
