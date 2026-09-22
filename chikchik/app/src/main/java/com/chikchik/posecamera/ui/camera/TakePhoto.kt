package com.chikchik.posecamera.ui.camera

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.core.content.ContextCompat
import com.chikchik.posecamera.util.newCaptureFile
import java.io.File

/**
 * CameraX [ImageCapture] configured for the best quality the camera offers: highest
 * available resolution at the requested [aspectRatio] and full JPEG quality.
 *
 * [aspectRatio] must be one of [androidx.camera.core.AspectRatio.RATIO_4_3] (the sensor's
 * native ratio on almost every device) or [androidx.camera.core.AspectRatio.RATIO_16_9].
 * This only picks which of the camera's own native output sizes to use - it is a real,
 * hardware-reported resolution, never an upscaled or simulated crop.
 *
 * Capture mode is [ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY], not MAXIMIZE_QUALITY:
 * MAXIMIZE_QUALITY lets CameraX run extra internal processing (e.g. multi-frame
 * fusion) before `onImageSaved` fires, which is what caused the noticeable delay
 * between tapping the shutter and the capture preview appearing. MINIMIZE_LATENCY
 * uses the same resolution and the same setJpegQuality(100) below, so output size
 * and compression are unchanged - only the extra processing time is removed.
 */
fun buildImageCapture(aspectRatio: Int = androidx.camera.core.AspectRatio.RATIO_4_3): ImageCapture {
    val aspectRatioStrategy = if (aspectRatio == androidx.camera.core.AspectRatio.RATIO_16_9) {
        AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
    } else {
        AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
    }
    val resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(aspectRatioStrategy)
        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
        .build()

    return ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(resolutionSelector)
        .setJpegQuality(100)
        .build()
}

/**
 * Takes ONE picture with CameraX [ImageCapture] and writes the camera's own
 * JPEG to a temp file.
 *
 * This is the real camera image: the reference overlay, the controls and every
 * other Compose UI element live in separate layers and are never part of it.
 * (No screenshot / view capture is used anywhere.)
 */
fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onCaptured: (File) -> Unit,
    onFailure: () -> Unit,
) {
    val file = newCaptureFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    try {
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onCaptured(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    file.delete()
                    onFailure()
                }
            }
        )
    } catch (e: Exception) {
        // e.g. camera not bound yet
        e.printStackTrace()
        file.delete()
        onFailure()
    }
}
