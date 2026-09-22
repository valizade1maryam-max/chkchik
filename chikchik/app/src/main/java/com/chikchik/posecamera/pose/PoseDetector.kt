package com.chikchik.posecamera.pose

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Below this per-point likelihood a keypoint is not counted as "confident" (it is still stored).
 * Shared with [LivePoseAnalyzer.kt][LivePoseAnalyzer] (Stage 11) so a live pose is judged by
 * exactly the same rule as a reference-photo pose.
 */
internal const val MIN_KEYPOINT_CONFIDENCE = 0.5f

/** How many of the 14 core keypoints must be confident for the whole pose to count as [PoseDetectionStatus.SUCCESS]. */
internal const val MIN_CONFIDENT_CORE_KEYPOINTS = 10

/** Maps every landmark the model can output to our own [PoseKeypointType]. NECK has no model output; it is derived below. */
internal val LANDMARK_TYPE_MAP: Map<Int, PoseKeypointType> = mapOf(
    PoseLandmark.NOSE to PoseKeypointType.HEAD,
    PoseLandmark.LEFT_EYE to PoseKeypointType.LEFT_EYE,
    PoseLandmark.RIGHT_EYE to PoseKeypointType.RIGHT_EYE,
    PoseLandmark.LEFT_EAR to PoseKeypointType.LEFT_EAR,
    PoseLandmark.RIGHT_EAR to PoseKeypointType.RIGHT_EAR,
    PoseLandmark.LEFT_SHOULDER to PoseKeypointType.LEFT_SHOULDER,
    PoseLandmark.RIGHT_SHOULDER to PoseKeypointType.RIGHT_SHOULDER,
    PoseLandmark.LEFT_ELBOW to PoseKeypointType.LEFT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to PoseKeypointType.RIGHT_ELBOW,
    PoseLandmark.LEFT_WRIST to PoseKeypointType.LEFT_WRIST,
    PoseLandmark.RIGHT_WRIST to PoseKeypointType.RIGHT_WRIST,
    PoseLandmark.LEFT_HIP to PoseKeypointType.LEFT_HIP,
    PoseLandmark.RIGHT_HIP to PoseKeypointType.RIGHT_HIP,
    PoseLandmark.LEFT_KNEE to PoseKeypointType.LEFT_KNEE,
    PoseLandmark.RIGHT_KNEE to PoseKeypointType.RIGHT_KNEE,
    PoseLandmark.LEFT_ANKLE to PoseKeypointType.LEFT_ANKLE,
    PoseLandmark.RIGHT_ANKLE to PoseKeypointType.RIGHT_ANKLE,
    PoseLandmark.LEFT_HEEL to PoseKeypointType.LEFT_HEEL,
    PoseLandmark.RIGHT_HEEL to PoseKeypointType.RIGHT_HEEL,
    PoseLandmark.LEFT_FOOT_INDEX to PoseKeypointType.LEFT_FOOT_INDEX,
    PoseLandmark.RIGHT_FOOT_INDEX to PoseKeypointType.RIGHT_FOOT_INDEX,
    PoseLandmark.LEFT_THUMB to PoseKeypointType.LEFT_THUMB,
    PoseLandmark.RIGHT_THUMB to PoseKeypointType.RIGHT_THUMB,
    PoseLandmark.LEFT_INDEX to PoseKeypointType.LEFT_INDEX,
    PoseLandmark.RIGHT_INDEX to PoseKeypointType.RIGHT_INDEX,
    PoseLandmark.LEFT_PINKY to PoseKeypointType.LEFT_PINKY,
    PoseLandmark.RIGHT_PINKY to PoseKeypointType.RIGHT_PINKY,
)

/**
 * Runs on-device pose detection on [bitmap] (the decoded reference photo) and returns a
 * normalized [PoseDetectionResult].
 *
 * Fully on-device: the "accurate" bundled ML Kit model ships inside the app, so this never
 * makes a network call and never depends on Play Services being present. Runs once per
 * still image (SINGLE_IMAGE_MODE) rather than the streaming/tracking mode meant for live
 * camera frames.
 *
 * Never throws - any failure (corrupt bitmap, detector error) is reported as
 * [PoseDetectionStatus.FAILED] so a bad reference photo can never crash the app.
 */
suspend fun detectPose(bitmap: Bitmap): PoseDetectionResult = withContext(Dispatchers.Default) {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) {
        return@withContext PoseDetectionResult(status = PoseDetectionStatus.FAILED)
    }

    val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
        .build()
    val detector = PoseDetection.getClient(options)
    try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val pose = suspendCancellableCoroutine { continuation ->
            detector.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }
        if (pose == null) {
            PoseDetectionResult(status = PoseDetectionStatus.FAILED)
        } else {
            toPoseDetectionResult(pose, width, height)
        }
    } catch (e: Exception) {
        PoseDetectionResult(status = PoseDetectionStatus.FAILED)
    } finally {
        detector.close()
    }
}

/**
 * Converts a raw ML Kit [Pose] into our own, model-independent data structure.
 *
 * `internal` (Stage 11): also called by [LivePoseAnalyzer.kt][LivePoseAnalyzer] for live camera
 * frames, so a live pose and a reference-photo pose always come out of the exact same shape -
 * required for comparing them in a later stage - instead of two separate conversion paths.
 */
internal fun toPoseDetectionResult(pose: Pose, imageWidth: Int, imageHeight: Int): PoseDetectionResult {
    val landmarks = pose.allPoseLandmarks
    if (landmarks.isEmpty()) {
        // No detectable person at all (empty photo, object-only photo, unreadable body, ...).
        return PoseDetectionResult(status = PoseDetectionStatus.NO_PERSON_DETECTED)
    }

    val keypoints = mutableMapOf<PoseKeypointType, PoseKeypoint>()
    for (landmark in landmarks) {
        val type = LANDMARK_TYPE_MAP[landmark.landmarkType] ?: continue
        val position = landmark.position
        keypoints[type] = PoseKeypoint(
            type = type,
            xNorm = position.x / imageWidth,
            yNorm = position.y / imageHeight,
            confidence = landmark.inFrameLikelihood,
        )
    }

    // The model has no neck landmark; derive one as the shoulder midpoint when both shoulders
    // were found, so "neck" from the Stage 10 spec is still available downstream.
    val leftShoulder = keypoints[PoseKeypointType.LEFT_SHOULDER]
    val rightShoulder = keypoints[PoseKeypointType.RIGHT_SHOULDER]
    if (leftShoulder != null && rightShoulder != null) {
        keypoints[PoseKeypointType.NECK] = PoseKeypoint(
            type = PoseKeypointType.NECK,
            xNorm = (leftShoulder.xNorm + rightShoulder.xNorm) / 2f,
            yNorm = (leftShoulder.yNorm + rightShoulder.yNorm) / 2f,
            confidence = minOf(leftShoulder.confidence, rightShoulder.confidence),
            isDerived = true,
        )
    }

    val confidentCoreCount = PoseKeypointType.CORE.count { type ->
        (keypoints[type]?.confidence ?: 0f) >= MIN_KEYPOINT_CONFIDENCE
    }
    val coreConfidences = PoseKeypointType.CORE.mapNotNull { keypoints[it]?.confidence }
    val overallConfidence = if (coreConfidences.isEmpty()) 0f else coreConfidences.average().toFloat()

    val snapshot = PoseSnapshot(
        keypoints = keypoints,
        overallConfidence = overallConfidence,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
    )

    // A body was found, but not reliably enough to hand to a future comparison/coaching stage:
    // keep the (partial/uncertain) data, just flag it instead of pretending it is trustworthy.
    val status = if (confidentCoreCount >= MIN_CONFIDENT_CORE_KEYPOINTS) {
        PoseDetectionStatus.SUCCESS
    } else {
        PoseDetectionStatus.LOW_CONFIDENCE
    }

    return PoseDetectionResult(status = status, poses = listOf(snapshot))
}
