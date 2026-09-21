package com.chikchik.posecamera.ui.camera

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/**
 * A camera that is currently bound to the lifecycle, plus what the UI needs
 * to control it (zoom, flash, tap-to-focus, lens switching).
 *
 * Everything here talks to CameraX only - none of it touches the captured image.
 */
class BoundCamera(
    val camera: Camera,
    private val previewView: PreviewView,
    /** The lens that is really in use (may differ from the requested one if it is missing). */
    val lensFacing: Int,
    hasBackCamera: Boolean,
    hasFrontCamera: Boolean,
) {
    /** True only when the device has both a front and a back camera. */
    val canSwitchLens: Boolean = hasBackCamera && hasFrontCamera

    /** True when the bound camera has a flash unit (front cameras usually do not). */
    val hasFlash: Boolean = try {
        camera.cameraInfo.hasFlashUnit()
    } catch (e: Exception) {
        false
    }

    /**
     * Focus + metering at a point in [PreviewView] pixel coordinates.
     * Returns true if the request was sent, false if the device does not
     * support it (or anything went wrong) - it never throws.
     */
    fun focusAt(x: Float, y: Float): Boolean {
        return try {
            val point = previewView.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            if (camera.cameraInfo.isFocusMeteringSupported(action)) {
                camera.cameraControl.startFocusAndMetering(action)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Sets the zoom as a 0..1 "perceptually linear" value. Never throws. */
    fun setLinearZoom(value: Float) {
        try {
            camera.cameraControl.setLinearZoom(value.coerceIn(0f, 1f))
        } catch (e: Exception) {
            // ignore: camera may be closing
        }
    }
}

/**
 * Live camera preview backed by CameraX [PreviewView].
 *
 * Binds two CameraX use cases to the same camera:
 *  - [Preview]      -> what the user sees on screen
 *  - [ImageCapture] -> what is actually recorded when the shutter is pressed
 *
 * The preview is a plain Android View; anything drawn on top of it in Compose
 * (like the reference-image overlay) is a separate layer and is never part of
 * the camera image.
 *
 * Changing [lensFacing] re-binds the same use cases to the other camera.
 * [onCameraChanged] gets the new [BoundCamera] after every successful bind,
 * and null while no camera is bound.
 * [onCameraError] is called when no camera can be used at all (no camera on the device,
 * camera service failure, ...), so the UI can tell the user instead of showing a black screen.
 *
 * Resources: everything is unbound when this composable leaves the screen, and the
 * display-rotation listener is unregistered, so the camera is never held in the background.
 */
@Composable
fun CameraPreview(
    imageCapture: ImageCapture,
    lensFacing: Int,
    onCameraChanged: (BoundCamera?) -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCameraChanged by rememberUpdatedState(onCameraChanged)
    val currentOnCameraError by rememberUpdatedState(onCameraError)

    val previewView = remember {
        PreviewView(context).apply {
            // TextureView-based mode: plays nicely with Compose layers drawn above it.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView, imageCapture, lensFacing) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var previewUseCase: Preview? = null

        // Keeps preview + saved-photo orientation correct when the screen turns by 180 degrees
        // (portrait <-> reverse portrait / landscape <-> reverse landscape does not re-create
        // the Activity, so nothing else would notice).
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val display = previewView.display ?: return
                if (display.displayId != displayId) return
                previewUseCase?.targetRotation = display.rotation
                imageCapture.targetRotation = display.rotation
            }
        }
        displayManager?.registerDisplayListener(displayListener, null)

        providerFuture.addListener({
            if (disposed) return@addListener

            try {
                val cameraProvider = providerFuture.get()

                val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

                // Use the requested lens if the device has it, otherwise whatever exists.
                val actualLens = when {
                    lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront ->
                        CameraSelector.LENS_FACING_FRONT
                    lensFacing == CameraSelector.LENS_FACING_BACK && hasBack ->
                        CameraSelector.LENS_FACING_BACK
                    hasBack -> CameraSelector.LENS_FACING_BACK
                    hasFront -> CameraSelector.LENS_FACING_FRONT
                    else -> {
                        // The device has no usable camera.
                        currentOnCameraError()
                        return@addListener
                    }
                }
                val selector = CameraSelector.Builder()
                    .requireLensFacing(actualLens)
                    .build()

                // The view may not be attached yet (display == null): fall back to the default display.
                val rotation = previewView.display?.rotation
                    ?: displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
                    ?: Surface.ROTATION_0

                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                previewUseCase = preview

                // Keeps the saved photo's orientation (EXIF) in sync with the device.
                imageCapture.targetRotation = rotation

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, selector, preview, imageCapture
                )
                currentOnCameraChanged(
                    BoundCamera(camera, previewView, actualLens, hasBack, hasFront)
                )
            } catch (e: Exception) {
                // Camera service unavailable, camera disabled by policy, bind failed, ...
                currentOnCameraChanged(null)
                currentOnCameraError()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            displayManager?.unregisterDisplayListener(displayListener)
            currentOnCameraChanged(null)
            if (providerFuture.isDone) {
                try {
                    providerFuture.get().unbindAll()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
