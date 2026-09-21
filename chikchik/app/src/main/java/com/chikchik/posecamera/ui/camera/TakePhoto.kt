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
 * CameraX [ImageCapture] configured for the best quality the camera offers:
 * highest available resolution (4:3 = the sensor's native ratio) and
 * maximise-quality capture mode.
 */
fun buildImageCapture(): ImageCapture {
    val resolutionSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
        .build()

    return ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
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
