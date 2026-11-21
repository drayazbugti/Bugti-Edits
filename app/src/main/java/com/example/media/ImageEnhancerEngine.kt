package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import com.example.model.AspectRatioOption
import com.example.model.EffectMode
import com.example.model.EnhanceMode
import com.example.model.FilterPreset
import com.example.model.FrameStyle
import com.example.model.MediaSource
import com.example.model.OverlayMode
import com.example.model.StudioEditState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

object ImageEnhancerEngine {

    suspend fun loadSourceBitmap(
        context: Context,
        source: MediaSource,
        maxDimension: Int = 1440
    ): Bitmap? = withContext(Dispatchers.IO) {
        when (source) {
            is MediaSource.SampleDrawable -> {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(context.resources, source.resId, options)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val raw = BitmapFactory.decodeResource(context.resources, source.resId, decodeOpts)
                    ?: return@withContext null
                scaleToMax(raw, maxDimension)
            }
            is MediaSource.ContentUri -> {
                if (source.isVideo) return@withContext null
                val uri = source.uri
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                } ?: return@withContext null

                val oriented = applyExifOrientation(context, uri, raw)
                scaleToMax(oriented, maxDimension)
            }
        }
    }

    suspend fun processBitmap(
        source: Bitmap,
        editState: StudioEditState,
        isExport: Boolean = false
    ): Bitmap = withContext(Dispatchers.Default) {
        var working = applyGeometryAndCrop(source, editState)

        // Optional upscale during export for 4K / Ultra HD feel
        if (isExport && (editState.enhanceMode == EnhanceMode.ULTRA_HD ||
                editState.enhanceMode == EnhanceMode.AI_UHD ||
                editState.enhanceMode == EnhanceMode.ANIME_4K ||
                editState.exportResolution.maxDim >= 3840)
        ) {
            val targetMax = editState.exportResolution.maxDim
            val currentMax = max(working.width, working.height)
            if (currentMax < targetMax) {
                val scale = min(2.0f, targetMax.toFloat() / currentMax.toFloat())
                if (scale > 1.05f) {
                    val upscaled = Bitmap.createScaledBitmap(
                        working,
                        (working.width * scale).roundToInt(),
                        (working.height * scale).roundToInt(),
                        true
                    )
                    if (upscaled != working) {
                        working = upscaled
                    }
                }
            }
        }

        // 1. Skin Smooth / Soft Focus pass (soft blend)
        val totalSmooth = max(
            editState.skinSmooth,
            if (editState.enhanceMode == EnhanceMode.PORTRAIT) 0.25f * editState.enhanceStrength else 0f
        ).coerceIn(0f, 1f)
        if (totalSmooth > 0.02f) {
            working = applySoftSkinBlend(working, totalSmooth * 0.55f)
        }

        // 2. ColorMatrix pass: Enhance tonal boost + Filter Preset + Adjust + Retouch Glow/Hair/Makeup
        working = applyColorGrading(working, editState)

        // 3. Sharpening / Clarity convolution pass
        val enhanceSharpen = when (editState.enhanceMode) {
            EnhanceMode.NONE -> 0f
            EnhanceMode.HD -> 0.42f * editState.enhanceStrength
            EnhanceMode.ULTRA_HD -> 0.68f * editState.enhanceStrength
            EnhanceMode.PORTRAIT -> 0.35f * editState.enhanceStrength
            EnhanceMode.AI_UHD -> 0.85f * editState.enhanceStrength
            EnhanceMode.NIGHT_DENOISE -> 0.45f * editState.enhanceStrength
            EnhanceMode.ANIME_4K -> 0.80f * editState.enhanceStrength
            EnhanceMode.OLD_PHOTO -> 0.60f * editState.enhanceStrength
            EnhanceMode.MOTION_FIX -> 0.90f * editState.enhanceStrength
        }
        val totalSharpen = (enhanceSharpen + editState.sharpen).coerceIn(0f, 1.35f)
        if (totalSharpen > 0.04f) {
            working = applyUnsharpSharpen(working, totalSharpen)
        }

        // 4. Touch Removal (soft radial patch blur) & Mosaic Pixelation
        if (editState.removalPoints.isNotEmpty() || editState.mosaicPoints.isNotEmpty()) {
            working = applyMosaicAndRemoval(working, editState)
        }

        // 5. Effects, Overlays, Vignette, Frames
        working = applyEffectsOverlaysAndFrames(working, editState)

        // 6. Bake Text & Sticker Overlays only on export (live editor renders interactive Compose overlays)
        if (isExport && (editState.textOverlays.isNotEmpty() || editState.stickerOverlays.isNotEmpty())) {
            working = bakeCreativeOverlays(working, editState)
        }

        working
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "${displayName}_${System.currentTimeMillis()}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Bugti Editz")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Failed to create MediaStore image entry")
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
                } ?: error("Failed to open output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: context.filesDir
                val outDir = File(picturesDir, "Bugti Editz").apply { mkdirs() }
                val outFile = File(outDir, fileName)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }
                Uri.fromFile(outFile)
            }
        }
    }

    private fun applyGeometryAndCrop(src: Bitmap, state: StudioEditState): Bitmap {
        val matrix = Matrix()
        if (state.flipHorizontal) {
            matrix.postScale(-1f, 1f)
        }
        if (state.bodyBalance != 0f) {
            // Subtle horizontal/vertical proportion adjustment
            val scaleX = (1f - state.bodyBalance * 0.12f).coerceIn(0.85f, 1.15f)
            matrix.postScale(scaleX, 1f)
        }
        if (state.rotationDegrees != 0) {
            matrix.postRotate(state.rotationDegrees.toFloat())
        }

        val transformed = if (!matrix.isIdentity) {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } else {
            src
        }

        val targetRatio = state.aspectRatio.ratio ?: return transformed
        val curRatio = transformed.width.toFloat() / transformed.height.toFloat()
        if (kotlin.math.abs(curRatio - targetRatio) < 0.01f) return transformed

        val cropW: Int
        val cropH: Int
        if (curRatio > targetRatio) {
            cropH = transformed.height
            cropW = (cropH * targetRatio).roundToInt().coerceIn(1, transformed.width)
        } else {
            cropW = transformed.width
            cropH = (cropW / targetRatio).roundToInt().coerceIn(1, transformed.height)
        }
        val left = ((transformed.width - cropW) / 2).coerceAtLeast(0)
        val top = ((transformed.height - cropH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(transformed, left, top, cropW, cropH)
    }

    private fun applyColorGrading(src: Bitmap, state: StudioEditState): Bitmap {
        val cm = ColorMatrix()

        // Enhance mode baseline contrast/saturation/brightness
        val enhStr = state.enhanceStrength
        val enhContrast = when (state.enhanceMode) {
            EnhanceMode.NONE -> 0f
            EnhanceMode.HD -> 0.10f * enhStr
            EnhanceMode.ULTRA_HD -> 0.16f * enhStr
            EnhanceMode.PORTRAIT -> 0.08f * enhStr
            EnhanceMode.AI_UHD -> 0.22f * enhStr
            EnhanceMode.NIGHT_DENOISE -> 0.14f * enhStr
            EnhanceMode.ANIME_4K -> 0.20f * enhStr
            EnhanceMode.OLD_PHOTO -> 0.26f * enhStr
            EnhanceMode.MOTION_FIX -> 0.14f * enhStr
        }
        val enhSat = when (state.enhanceMode) {
            EnhanceMode.NONE -> 0f
            EnhanceMode.HD -> 0.08f * enhStr
            EnhanceMode.ULTRA_HD -> 0.15f * enhStr
            EnhanceMode.PORTRAIT -> 0.06f * enhStr
            EnhanceMode.AI_UHD -> 0.18f * enhStr
            EnhanceMode.NIGHT_DENOISE -> 0.10f * enhStr
            EnhanceMode.ANIME_4K -> 0.28f * enhStr
            EnhanceMode.OLD_PHOTO -> 0.18f * enhStr
            EnhanceMode.MOTION_FIX -> 0.08f * enhStr
        }
        val enhBright = when (state.enhanceMode) {
            EnhanceMode.NIGHT_DENOISE -> 0.14f * enhStr
            EnhanceMode.PORTRAIT -> 0.05f * enhStr
            else -> 0f
        }

        // Total brightness (-128..128), contrast (0.5..1.8), saturation (0..2)
        val totalBrightness = (state.brightness + enhBright + state.faceGlow * 0.12f) * 255f * 0.5f
        val totalContrast = (1f + state.contrast + enhContrast + state.hairGloss * 0.15f).coerceIn(0.4f, 2.0f)
        val totalSat = (1f + state.saturation + enhSat + state.makeupTint * 0.22f).coerceIn(0f, 2.2f)

        val satMatrix = ColorMatrix().apply { setSaturation(totalSat) }
        cm.postConcat(satMatrix)

        val scale = totalContrast
        val translate = (-0.5f * scale + 0.5f) * 255f + totalBrightness
        val warmthRed = state.warmth * 36f + state.makeupTint * 12f
        val warmthBlue = -state.warmth * 36f
        val adjustMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate + warmthRed,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate + warmthBlue,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(adjustMatrix)

        // Filter Preset Color Matrix blended by filterIntensity
        if (state.filterPreset != FilterPreset.NATURAL && state.filterIntensity > 0.01f) {
            val presetMatrix = buildFilterPresetMatrix(state.filterPreset, state.filterIntensity)
            cm.postConcat(presetMatrix)
        }

        val out = createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun buildFilterPresetMatrix(preset: FilterPreset, intensity: Float): ColorMatrix {
        val t = intensity.coerceIn(0f, 1f)
        val target = when (preset) {
            FilterPreset.NATURAL -> floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.TOKYO_NIGHT -> floatArrayOf(
                0.92f, 0f, 0.08f, 0f, -8f,
                0f, 1.04f, 0.08f, 0f, 2f,
                0.05f, 0.10f, 1.18f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.GOLDEN_HOUR -> floatArrayOf(
                1.16f, 0.05f, 0f, 0f, 14f,
                0.04f, 1.04f, 0f, 0f, 6f,
                0f, 0f, 0.84f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.TEAL_ORANGE -> floatArrayOf(
                1.18f, 0.04f, -0.08f, 0f, 10f,
                0f, 1.02f, 0.04f, 0f, 0f,
                -0.08f, 0.12f, 1.12f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.CRISP_BW -> floatArrayOf(
                0.33f, 0.59f, 0.08f, 0f, 0f,
                0.33f, 0.59f, 0.08f, 0f, 0f,
                0.33f, 0.59f, 0.08f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.VELVET_MATTE -> floatArrayOf(
                0.96f, 0.04f, 0.02f, 0f, 14f,
                0.02f, 0.94f, 0.04f, 0f, 10f,
                0.04f, 0.02f, 0.98f, 0f, 16f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.CYBER_NEON -> floatArrayOf(
                1.12f, 0f, 0.14f, 0f, 6f,
                0f, 0.96f, 0.10f, 0f, -4f,
                0.16f, 0.06f, 1.20f, 0f, 14f,
                0f, 0f, 0f, 1f, 0f
            )
            FilterPreset.FUJI_WARM -> floatArrayOf(
                1.08f, 0.04f, 0f, 0f, 8f,
                0.02f, 1.06f, 0.02f, 0f, 4f,
                0f, 0.04f, 0.92f, 0f, 2f,
                0f, 0f, 0f, 1f, 0f
            )
        }
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val blended = FloatArray(20) { i ->
            identity[i] + (target[i] - identity[i]) * t
        }
        return ColorMatrix(blended)
    }

    /**
     * Fast box-downsample + upsample soft blend for skin smoothing / soft focus.
     */
    private fun applySoftSkinBlend(src: Bitmap, alphaFraction: Float): Bitmap {
        val w = src.width
        val h = src.height
        val downW = max(8, w / 6)
        val downH = max(8, h / 6)
        val tiny = Bitmap.createScaledBitmap(src, downW, downH, true)
        val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
        tiny.recycle()

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (alphaFraction.coerceIn(0f, 1f) * 255f).roundToInt()
        }
        canvas.drawBitmap(blurred, 0f, 0f, paint)
        blurred.recycle()
        return out
    }

    /**
     * Unsharp-mask sharpening: enhanced = original + strength * (original - blurred)
     * Runs cleanly in Kotlin integer math on ARGB pixels for responsive studio clarity.
     */
    private fun applyUnsharpSharpen(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 4 || h < 4) return src

        val blurDownW = max(4, (w * 0.5f).roundToInt())
        val blurDownH = max(4, (h * 0.5f).roundToInt())
        val small = Bitmap.createScaledBitmap(src, blurDownW, blurDownH, true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()

        val total = w * h
        val origPixels = IntArray(total)
        val blurPixels = IntArray(total)
        src.getPixels(origPixels, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        blurred.recycle()

        val strength = amount.coerceIn(0f, 1.5f) * 1.15f
        val outPixels = IntArray(total)

        for (i in 0 until total) {
            val o = origPixels[i]
            val b = blurPixels[i]
            val a = (o ushr 24) and 0xFF
            val or = (o ushr 16) and 0xFF
            val og = (o ushr 8) and 0xFF
            val ob = o and 0xFF

            val br = (b ushr 16) and 0xFF
            val bg = (b ushr 8) and 0xFF
            val bb = b and 0xFF

            val r = (or + ((or - br) * strength)).roundToInt().coerceIn(0, 255)
            val g = (og + ((og - bg) * strength)).roundToInt().coerceIn(0, 255)
            val bl = (ob + ((ob - bb) * strength)).roundToInt().coerceIn(0, 255)

            outPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or bl
        }

        return createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, w, 0, 0, w, h)
        }
    }

    private fun applyMosaicAndRemoval(src: Bitmap, state: StudioEditState): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        // Pixelated Mosaic blocks
        if (state.mosaicPoints.isNotEmpty()) {
            val tiny = Bitmap.createScaledBitmap(src, max(6, src.width / 24), max(6, src.height / 24), false)
            val pixelated = Bitmap.createScaledBitmap(tiny, src.width, src.height, false)
            tiny.recycle()
            val radius = max(18f, min(w, h) * 0.055f)
            val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            state.mosaicPoints.forEach { pt ->
                val cx = pt.x * w
                val cy = pt.y * h
                canvas.save()
                val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                val path = android.graphics.Path().apply { addOval(rect, android.graphics.Path.Direction.CW) }
                canvas.clipPath(path)
                canvas.drawBitmap(pixelated, 0f, 0f, clipPaint)
                canvas.restore()
            }
            pixelated.recycle()
        }

        // Soft Removal Patch Heal
        if (state.removalPoints.isNotEmpty()) {
            val tiny = Bitmap.createScaledBitmap(src, max(6, src.width / 10), max(6, src.height / 10), true)
            val healed = Bitmap.createScaledBitmap(tiny, src.width, src.height, true)
            tiny.recycle()
            val radius = max(20f, min(w, h) * 0.05f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            state.removalPoints.forEach { pt ->
                val cx = pt.x * w
                val cy = pt.y * h
                canvas.save()
                val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                val path = android.graphics.Path().apply { addOval(rect, android.graphics.Path.Direction.CW) }
                canvas.clipPath(path)
                canvas.drawBitmap(healed, 0f, 0f, paint)
                canvas.restore()
            }
            healed.recycle()
        }
        return out
    }

    private fun applyEffectsOverlaysAndFrames(src: Bitmap, state: StudioEditState): Bitmap {
        val needsFx = state.effectMode != EffectMode.NONE ||
            state.overlayMode != OverlayMode.NONE ||
            state.vignette > 0.02f ||
            state.frameStyle != FrameStyle.NONE
        if (!needsFx) return src

        var working = src.copy(Bitmap.Config.ARGB_8888, true)

        if (state.effectMode == EffectMode.SOFT_FOCUS || state.effectMode == EffectMode.GLOW_BLOOM) {
            working = applySoftSkinBlend(working, state.effectIntensity * 0.45f)
        }

        val canvas = Canvas(working)
        val w = working.width.toFloat()
        val h = working.height.toFloat()

        // Film grain or haze
        if (state.effectMode == EffectMode.FILM_GRAIN) {
            val grainPaint = Paint().apply {
                alpha = (state.effectIntensity * 45).roundToInt().coerceIn(8, 55)
            }
            val rng = Random(42)
            val step = max(3, (min(w, h) / 180f).roundToInt())
            var y = 0
            while (y < working.height) {
                var x = 0
                while (x < working.width) {
                    val v = rng.nextInt(256)
                    grainPaint.color = Color.argb(grainPaint.alpha, v, v, v)
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + step).toFloat(), (y + step).toFloat(), grainPaint)
                    x += step
                }
                y += step
            }
        } else if (state.effectMode == EffectMode.CINEMA_HAZE) {
            canvas.drawColor(Color.argb((state.effectIntensity * 42).roundToInt(), 210, 195, 225))
        }

        // Light Leak / Prism Overlays
        when (state.overlayMode) {
            OverlayMode.NONE -> Unit
            OverlayMode.SUNSET_LEAK -> {
                val p = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, w * 0.7f, h * 0.7f,
                        Color.argb((state.overlayIntensity * 140).roundToInt(), 255, 118, 64),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w, h, p)
            }
            OverlayMode.PRISM_FLARE -> {
                val p = Paint().apply {
                    shader = LinearGradient(
                        w, 0f, 0f, h,
                        Color.argb((state.overlayIntensity * 130).roundToInt(), 74, 219, 200),
                        Color.argb((state.overlayIntensity * 90).roundToInt(), 255, 92, 168),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w, h, p)
            }
            OverlayMode.STUDIO_DUO -> {
                val p = Paint().apply {
                    shader = LinearGradient(
                        0f, h, w, 0f,
                        Color.argb((state.overlayIntensity * 115).roundToInt(), 226, 194, 117),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w, h, p)
            }
        }

        // Vignette
        if (state.vignette > 0.02f) {
            val radius = max(w, h) * 0.75f
            val vigPaint = Paint().apply {
                shader = RadialGradient(
                    w / 2f, h / 2f, radius,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        Color.argb((state.vignette * 210).roundToInt().coerceIn(0, 225), 0, 0, 0)
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w, h, vigPaint)
        }

        // Frames
        when (state.frameStyle) {
            FrameStyle.NONE -> Unit
            FrameStyle.CINEMA_BARS -> {
                val barH = h * 0.11f
                val barPaint = Paint().apply { color = Color.BLACK }
                canvas.drawRect(0f, 0f, w, barH, barPaint)
                canvas.drawRect(0f, h - barH, w, h, barPaint)
            }
            FrameStyle.POLAROID -> {
                val pad = min(w, h) * 0.045f
                val bottomPad = min(w, h) * 0.14f
                val outW = working.width + (pad * 2).roundToInt()
                val outH = working.height + (pad + bottomPad).roundToInt()
                val framed = createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val fCanvas = Canvas(framed)
                fCanvas.drawColor(Color.parseColor("#F5F3EF"))
                fCanvas.drawBitmap(working, pad, pad, null)
                return framed
            }
            FrameStyle.DARK_FILM -> {
                val stroke = min(w, h) * 0.035f
                val borderPaint = Paint().apply {
                    color = Color.parseColor("#121112")
                    style = Paint.Style.STROKE
                    strokeWidth = stroke * 2f
                }
                canvas.drawRect(0f, 0f, w, h, borderPaint)
            }
        }

        return working
    }

    private fun bakeCreativeOverlays(src: Bitmap, state: StudioEditState): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val baseSize = min(w, h) * 0.055f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(8f, 0f, 3f, Color.argb(170, 0, 0, 0))
        }

        state.textOverlays.forEach { item ->
            textPaint.color = item.colorArgb.toInt()
            textPaint.textSize = baseSize * item.scale
            canvas.drawText(item.text, item.normX * w, item.normY * h, textPaint)
        }

        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 18, 17, 18)
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val stickerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        state.stickerOverlays.forEach { sticker ->
            stickerTextPaint.textSize = baseSize * 0.72f * sticker.scale
            val bounds = Rect()
            stickerTextPaint.getTextBounds(sticker.label, 0, sticker.label.length, bounds)
            val cx = sticker.normX * w
            val cy = sticker.normY * h
            val padX = baseSize * 0.6f
            val padY = baseSize * 0.38f
            val pill = RectF(
                cx - bounds.width() / 2f - padX,
                cy - bounds.height() / 2f - padY,
                cx + bounds.width() / 2f + padX,
                cy + bounds.height() / 2f + padY
            )
            val corner = pill.height() / 2f
            canvas.drawRoundRect(pill, corner, corner, badgeBgPaint)
            canvas.drawRoundRect(pill, corner, corner, badgeBorderPaint)
            canvas.drawText(sticker.label, cx, cy + bounds.height() / 2f, stickerTextPaint)
        }

        return out
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        while (width / inSampleSize > maxDim * 2 || height / inSampleSize > maxDim * 2) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleToMax(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val curMax = max(w, h)
        if (curMax <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / curMax.toFloat()
        val targetW = max(1, (w * ratio).roundToInt())
        val targetH = max(1, (h * ratio).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
