package com.chikchik.posecamera.pose

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.Executor

/**
 * Stage 11: minimum time between two pose-detection runs. Camera frames arrive much faster
 * than this (usually 30/sec); most of them are only closed and skipped here so pose processing
 * never runs faster than needed, independently of the camera preview's own frame rate.
 */
private const val MIN_PROCESS_INTERVAL_MS = 150L

/**
 * [ImageAnalysis.Analyzer] that turns live camera frames into [PoseDetectionResult]s, using the
 * same normalized shape (via [toPoseDetectionResult]) as the one-shot reference-photo detector
 * in [PoseDetector.kt][detectPose] - so a later stage can compare the two directly.
 *
 * Differences from the reference-photo path, both existing only to keep the live camera smooth:
 *  - Uses the bundled "base" model in `STREAM_MODE` (lighter and meant for consecutive frames),
 *    not the "accurate" one-shot model used for a still reference photo.
 *  - Reads each [ImageProxy] directly ([InputImage.fromMediaImage]) - no [android.graphics.Bitmap]
 *    is ever created or converted per frame.
 *  - Throttled to [MIN_PROCESS_INTERVAL_MS] and never overlaps a run still in flight, so a slow
 *    device simply detects less often instead of falling behind or blocking the preview/shutter.
 *
 * Never throws back into CameraX: every path below closes [ImageProxy] and reports failures as
 * [PoseDetectionStatus.FAILED] through [onResult] instead of crashing the analyzer thread.
 *
 * [mainExecutor] runs [onResult] on the main thread so it can update Compose state directly.
 * Call [close] when live detection is no longer needed (e.g. the camera screen goes away) to
 * release the underlying ML Kit detector.
 */
class LivePoseAnalyzer(
    private val mainExecutor: Executor,
    private val onResult: (result: PoseDetectionResult, imageWidth: Int, imageHeight: Int) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @Volatile private var lastProcessedAtMs = 0L
    @Volatile private var busy = false
    @Volatile private var closed = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (closed || busy || now - lastProcessedAtMs < MIN_PROCESS_INTERVAL_MS) {
            // Skip this frame without ever touching the model - keeps preview/shutter smooth.
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // ML Kit reports landmark positions in the coordinate space of the *rotated* image, so
        // width/height must be swapped to match whenever the frame is rotated by 90/270.
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val rotated = rotationDegrees % 180 != 0
        val effectiveWidth = if (rotated) imageProxy.height else imageProxy.width
        val effectiveHeight = if (rotated) imageProxy.width else imageProxy.height

        busy = true
        lastProcessedAtMs = now
        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            detector.process(inputImage)
                .addOnSuccessListener(mainExecutor) { pose ->
                    if (!closed) onResult(toPoseDetectionResult(pose, effectiveWidth, effectiveHeight), effectiveWidth, effectiveHeight)
                }
                .addOnFailureListener(mainExecutor) {
                    if (!closed) onResult(PoseDetectionResult(status = PoseDetectionStatus.FAILED), effectiveWidth, effectiveHeight)
                }
                .addOnCompleteListener {
                    busy = false
                    imageProxy.close()
                }
        } catch (e: Exception) {
            busy = false
            imageProxy.close()
        }
    }

    /** Releases the underlying ML Kit detector. Never throws. Safe to call more than once. */
    fun close() {
        closed = true
        try {
            detector.close()
        } catch (e: Exception) {
            // ignore - detector may already be releasing
        }
    }
}
