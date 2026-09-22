package com.chikchik.posecamera.ui.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExposureState
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
import kotlin.math.roundToInt

/**
 * The real-world role of a physical lens, inferred at runtime from what the
 * device's own Camera2 characteristics report for it - never a fixed,
 * per-model list.
 */
enum class LensType { MAIN, ULTRA_WIDE, TELEPHOTO, MACRO, OTHER }

/**
 * One physical (or logical-default) lens the device exposes for a given
 * [CameraSelector] lens facing, discovered via CameraX's [ProcessCameraProvider]
 * on THIS device at runtime.
 *
 * [cameraId] is the Camera2 id used to rebind to this exact lens.
 * [intrinsicZoomRatio] is CameraX's field-of-view ratio versus the device's
 * default lens for this facing (1.0 = default, less than 1 = wider, more than 1 = narrower) -
 * see [CameraInfo.getIntrinsicZoomRatio].
 */
data class LensOption(
    val cameraId: String,
    val type: LensType,
    val intrinsicZoomRatio: Float,
)

/**
 * A camera that is currently bound to the lifecycle, plus what the UI needs
 * to control it (zoom, flash, tap-to-focus, lens switching).
 *
 * Everything here talks to CameraX only - none of it touches the captured image.
 */
class BoundCamera(
    val camera: Camera,
    private val previewView: PreviewView,
    /** The lens facing that is really in use (may differ from the requested one if it is missing). */
    val lensFacing: Int,
    hasBackCamera: Boolean,
    hasFrontCamera: Boolean,
    /** The physical lenses the device offers for [lensFacing] (empty if there is only one). */
    val lensOptions: List<LensOption> = emptyList(),
    /** Camera2 id of the lens actually bound right now, if known. */
    val activeLensOptionId: String? = null,
    /** CONTROL_AWB_MODE values this exact physical camera reports as available (Camera2). */
    val availableWhiteBalanceModes: List<Int> = emptyList(),
    /** Closest focus distance in diopters (1/meters) if the lens supports manual focus, else null. */
    val manualFocusMaxDiopters: Float? = null,
) {
    /** True only when the device has both a front and a back camera. */
    val canSwitchLens: Boolean = hasBackCamera && hasFrontCamera

    /** True when the bound camera has a flash unit (front cameras usually do not). */
    val hasFlash: Boolean = try {
        camera.cameraInfo.hasFlashUnit()
    } catch (e: Exception) {
        false
    }

    /** True when the Camera2 characteristics of this exact lens allow setting a manual focus distance. */
    val hasManualFocus: Boolean = manualFocusMaxDiopters != null

    /** Native CameraX exposure-compensation state (range, step, support) for the bound camera. */
    val exposureState: ExposureState get() = camera.cameraInfo.exposureState

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

    /**
     * Turns the continuous flashlight (torch) on or off. Distinct from [ImageCapture]'s
     * flash mode: torch stays lit continuously and is only offered when [hasFlash] is true,
     * since it uses the same LED unit. Never throws.
     */
    fun setTorch(enabled: Boolean) {
        if (!hasFlash) return
        try {
            camera.cameraControl.enableTorch(enabled)
        } catch (e: Exception) {
            // ignore: camera may be closing, or torch briefly unavailable
        }
    }

    /**
     * Sets exposure compensation by index (steps of [ExposureState.exposureCompensationStep]),
     * only when [ExposureState.isExposureCompensationSupported] is true. Never throws.
     */
    fun setExposureCompensationIndex(index: Int) {
        try {
            if (exposureState.isExposureCompensationSupported) {
                camera.cameraControl.setExposureCompensationIndex(index)
            }
        } catch (e: Exception) {
            // ignore: camera may be closing, or index momentarily out of range while it moves
        }
    }

    /**
     * Sets the Camera2 auto-white-balance mode (one of [availableWhiteBalanceModes]) via the
     * Camera2 interop extension point CameraX exposes for exactly this purpose. Never throws.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun setWhiteBalanceMode(awbMode: Int) {
        try {
            Camera2CameraControl.from(camera.cameraControl).addCaptureRequestOptions(
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, awbMode)
                    .build()
            )
        } catch (e: Exception) {
            // ignore: camera may be closing, or interop unsupported on this device
        }
    }

    /**
     * Sets a manual lens focus distance in diopters (0 = infinity, up to [manualFocusMaxDiopters]
     * = closest focus), switching AF off for as long as manual focus stays enabled. Only
     * meaningful when [hasManualFocus] is true. Never throws.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun setManualFocusDistance(diopters: Float) {
        if (!hasManualFocus) return
        try {
            Camera2CameraControl.from(camera.cameraControl).addCaptureRequestOptions(
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, diopters)
                    .build()
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Clears any manual-focus override, returning the lens to CameraX's normal continuous AF. */
    @OptIn(ExperimentalCamera2Interop::class)
    fun clearManualFocus() {
        try {
            Camera2CameraControl.from(camera.cameraControl).clearCaptureRequestOptions(
                CaptureRequestOptions.Builder()
                    .clearCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)
                    .clearCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE)
                    .build()
            )
        } catch (e: Exception) {
            // ignore
        }
    }
}

/** Camera2 minimum focus distance of [info] in meters, or null if the device doesn't report it. */
private fun minFocusDistanceMeters(info: CameraInfo): Float? {
    return try {
        val diopters = Camera2CameraInfo.from(info)
            .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        // 0 (or missing) means fixed-focus at infinity - no macro capability to report.
        if (diopters == null || diopters <= 0f) null else 1f / diopters
    } catch (e: Exception) {
        null
    }
}

/**
 * CONTROL_AWB_MODE values [info]'s Camera2 characteristics actually report as available on
 * this device, or an empty list if the characteristic is missing or there is only the
 * mandatory AUTO mode (nothing for the user to choose between).
 */
private fun availableWhiteBalanceModes(info: CameraInfo): List<Int> {
    return try {
        val modes = Camera2CameraInfo.from(info)
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            ?: return emptyList()
        val list = modes.toList()
        if (list.size <= 1) emptyList() else list
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Closest focus distance in diopters for [info], or null when the lens is fixed-focus or the
 * device does not let apps drive focus manually (no OFF entry in CONTROL_AF_AVAILABLE_MODES).
 */
private fun manualFocusMaxDiopters(info: CameraInfo): Float? {
    return try {
        val camera2Info = Camera2CameraInfo.from(info)
        val afModes = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        val supportsAfOff = afModes?.contains(CameraCharacteristics.CONTROL_AF_MODE_OFF) == true
        if (!supportsAfOff) return null
        val diopters = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        )
        if (diopters == null || diopters <= 0f) null else diopters
    } catch (e: Exception) {
        null
    }
}

/**
 * Classifies a lens using data CameraX/Camera2 actually report for it on this device:
 *  - a lens that focuses noticeably closer than the facing's default lens is a
 *    dedicated macro lens, regardless of its field of view;
 *  - otherwise, field of view versus the default lens ([intrinsicZoomRatio]) tells
 *    wide/ultra-wide/telephoto apart, the same way native camera apps group lenses.
 */
private fun classifyLens(
    intrinsicZoomRatio: Float,
    minFocusMeters: Float?,
    defaultMinFocusMeters: Float?,
): LensType = when {
    minFocusMeters != null && defaultMinFocusMeters != null &&
        minFocusMeters <= 0.06f && minFocusMeters < defaultMinFocusMeters * 0.6f -> LensType.MACRO
    intrinsicZoomRatio <= 0.8f -> LensType.ULTRA_WIDE
    intrinsicZoomRatio in 0.9f..1.1f -> LensType.MAIN
    intrinsicZoomRatio > 1.1f -> LensType.TELEPHOTO
    else -> LensType.OTHER
}

/**
 * Discovers the physical lenses [cameraProvider] exposes for [facing] on THIS device,
 * via CameraX's own camera list - never a hardcoded per-model set. Returns an empty
 * list when the facing has only a single (default) lens, since there is then nothing
 * for the user to choose between.
 */
private fun discoverLensOptions(cameraProvider: ProcessCameraProvider, facing: Int): List<LensOption> {
    return try {
        val facingSelector = CameraSelector.Builder().requireLensFacing(facing).build()
        val candidates = facingSelector.filter(cameraProvider.availableCameraInfos)
        if (candidates.size <= 1) return emptyList()

        // Reference point for spotting a dedicated macro lens: how close the
        // device's own default lens for this facing can focus.
        val defaultMinFocus = candidates.firstOrNull()?.let { minFocusDistanceMeters(it) }

        candidates.mapNotNull { info ->
            try {
                val id = Camera2CameraInfo.from(info).cameraId
                val ratio = info.intrinsicZoomRatio
                val type = classifyLens(ratio, minFocusDistanceMeters(info), defaultMinFocus)
                LensOption(id, type, ratio)
            } catch (e: Exception) {
                null
            }
        }
            // Some devices expose the same physical lens under more than one camera id
            // (e.g. a logical multi-camera alongside its own physical ids); collapse those.
            .distinctBy { "${it.type}-${(it.intrinsicZoomRatio * 10).roundToInt()}" }
            .sortedBy { it.intrinsicZoomRatio }
    } catch (e: Exception) {
        emptyList()
    }
}

/** Selector for [facing], optionally pinned to one physical lens by Camera2 id. */
private fun buildCameraSelector(facing: Int, cameraId: String?): CameraSelector {
    val builder = CameraSelector.Builder().requireLensFacing(facing)
    if (cameraId != null) {
        builder.addCameraFilter { infos ->
            infos.filter { Camera2CameraInfo.from(it).cameraId == cameraId }
        }
    }
    return builder.build()
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
 * Changing [lensOptionId] re-binds to a different physical lens on the SAME facing
 * (e.g. Ultra-Wide vs Main) without touching [lensFacing]; pass null to let the
 * device use its own default lens for that facing. If the id no longer matches an
 * available lens (e.g. left over from the other facing), it is ignored and the
 * default lens is used instead.
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
    lensOptionId: String? = null,
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

    DisposableEffect(lifecycleOwner, previewView, imageCapture, lensFacing, lensOptionId) {
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
                // Real physical lenses the device offers for this facing (Ultra-Wide,
                // Telephoto, Macro, ...), discovered from the device's own camera list -
                // never a fixed per-model list. Only use the requested lens id if it is
                // actually one of them; otherwise fall back to the facing's default lens.
                val lensOptions = discoverLensOptions(cameraProvider, actualLens)
                val requestedLensId = lensOptionId?.takeIf { id -> lensOptions.any { it.cameraId == id } }

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
                val camera = try {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        buildCameraSelector(actualLens, requestedLensId),
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    // The specific physical lens failed to bind (rare device quirk) -
                    // fall back to the facing's own default lens instead of failing outright.
                    if (requestedLensId != null) {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            buildCameraSelector(actualLens, null),
                            preview,
                            imageCapture
                        )
                    } else {
                        throw e
                    }
                }
                val activeLensId = try {
                    Camera2CameraInfo.from(camera.cameraInfo).cameraId
                } catch (e: Exception) {
                    null
                }
                currentOnCameraChanged(
                    BoundCamera(
                        camera, previewView, actualLens, hasBack, hasFront,
                        lensOptions, activeLensId,
                        availableWhiteBalanceModes(camera.cameraInfo),
                        manualFocusMaxDiopters(camera.cameraInfo)
                    )
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
