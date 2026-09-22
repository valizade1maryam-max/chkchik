package com.chikchik.posecamera.pose

/**
 * Stage 10: data model for a body pose extracted from a reference photo.
 *
 * This file only defines the shape of the data; [PoseDetector.kt][detectPose] fills it in.
 * Nothing here compares poses, scores them, or gives instructions - that is for later stages,
 * which can build entirely on top of [PoseDetectionResult] without needing another rewrite.
 */

/**
 * One tracked body point. [CORE] is the minimum set every later stage (comparison, coaching)
 * can expect to find in a successfully detected pose; everything below that in the
 * declaration is extra detail the underlying model happens to provide (face and hand/foot
 * detail) and is kept in case a future stage wants it.
 */
enum class PoseKeypointType {
    // --- Core points (required by the Stage 10 spec) ---
    HEAD,
    NECK,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,

    // --- Extra points the model can additionally provide ---
    LEFT_EYE, RIGHT_EYE,
    LEFT_EAR, RIGHT_EAR,
    LEFT_HEEL, RIGHT_HEEL,
    LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX,
    LEFT_THUMB, RIGHT_THUMB,
    LEFT_INDEX, RIGHT_INDEX,
    LEFT_PINKY, RIGHT_PINKY;

    companion object {
        /** The 14 points listed in the Stage 10 spec. */
        val CORE: List<PoseKeypointType> = listOf(
            HEAD, NECK,
            LEFT_SHOULDER, RIGHT_SHOULDER,
            LEFT_ELBOW, RIGHT_ELBOW,
            LEFT_WRIST, RIGHT_WRIST,
            LEFT_HIP, RIGHT_HIP,
            LEFT_KNEE, RIGHT_KNEE,
            LEFT_ANKLE, RIGHT_ANKLE,
        )
    }
}

/**
 * A single body point of a detected pose.
 *
 * @param xNorm horizontal position normalized to the source image: 0f = left edge, 1f =
 *   right edge. Independent of the photo's actual resolution or on-screen size.
 * @param yNorm vertical position normalized the same way: 0f = top edge, 1f = bottom edge.
 *   Values slightly outside 0f..1f are possible (a limb just past the frame edge) and are
 *   kept as-is rather than clamped, so a later stage can tell a point was near the border
 *   instead of confidently placed there.
 * @param confidence the model's own likelihood that this point is genuinely visible, 0f..1f.
 * @param isDerived true for a point that is not a direct model output but computed from other
 *   keypoints (currently only [PoseKeypointType.NECK], taken as the shoulder midpoint - the
 *   underlying pose model has no neck landmark of its own).
 */
data class PoseKeypoint(
    val type: PoseKeypointType,
    val xNorm: Float,
    val yNorm: Float,
    val confidence: Float,
    val isDerived: Boolean = false,
)

/** Two keypoints that form one "bone" of the skeleton - for whatever a later stage draws or compares. */
data class PoseConnection(val from: PoseKeypointType, val to: PoseKeypointType)

/** Skeleton edges between the core keypoints. */
val CORE_POSE_CONNECTIONS: List<PoseConnection> = listOf(
    PoseConnection(PoseKeypointType.HEAD, PoseKeypointType.NECK),
    PoseConnection(PoseKeypointType.NECK, PoseKeypointType.LEFT_SHOULDER),
    PoseConnection(PoseKeypointType.NECK, PoseKeypointType.RIGHT_SHOULDER),
    PoseConnection(PoseKeypointType.LEFT_SHOULDER, PoseKeypointType.LEFT_ELBOW),
    PoseConnection(PoseKeypointType.LEFT_ELBOW, PoseKeypointType.LEFT_WRIST),
    PoseConnection(PoseKeypointType.RIGHT_SHOULDER, PoseKeypointType.RIGHT_ELBOW),
    PoseConnection(PoseKeypointType.RIGHT_ELBOW, PoseKeypointType.RIGHT_WRIST),
    PoseConnection(PoseKeypointType.LEFT_SHOULDER, PoseKeypointType.LEFT_HIP),
    PoseConnection(PoseKeypointType.RIGHT_SHOULDER, PoseKeypointType.RIGHT_HIP),
    PoseConnection(PoseKeypointType.LEFT_HIP, PoseKeypointType.RIGHT_HIP),
    PoseConnection(PoseKeypointType.LEFT_HIP, PoseKeypointType.LEFT_KNEE),
    PoseConnection(PoseKeypointType.LEFT_KNEE, PoseKeypointType.LEFT_ANKLE),
    PoseConnection(PoseKeypointType.RIGHT_HIP, PoseKeypointType.RIGHT_KNEE),
    PoseConnection(PoseKeypointType.RIGHT_KNEE, PoseKeypointType.RIGHT_ANKLE),
)

/**
 * One detected body in an image.
 *
 * The underlying model only ever reports the single most prominent person in a photo, so
 * today there is always at most one [PoseSnapshot] inside a [PoseDetectionResult]. It is kept
 * as a `Map` here (rather than named fields) so a missing/undetected keypoint is simply absent
 * instead of needing a placeholder value.
 */
data class PoseSnapshot(
    val keypoints: Map<PoseKeypointType, PoseKeypoint>,
    /** Average confidence across the 14 core keypoints that were actually detected. */
    val overallConfidence: Float,
    /** Pixel size of the image the keypoints were normalized against (for debugging/logging only). */
    val imageWidth: Int,
    val imageHeight: Int,
) {
    fun keypoint(type: PoseKeypointType): PoseKeypoint? = keypoints[type]

    /** How many of the 14 core keypoints were detected at all (regardless of confidence). */
    val detectedCoreCount: Int
        get() = PoseKeypointType.CORE.count { keypoints.containsKey(it) }
}

/**
 * Stage 24: the LEFT_* <-> RIGHT_* counterpart of each keypoint type, for [mirrorPoseSnapshot].
 * HEAD and NECK have no side and map to themselves.
 */
private val MIRRORED_TYPE: Map<PoseKeypointType, PoseKeypointType> = buildMap {
    val pairs = listOf(
        PoseKeypointType.LEFT_SHOULDER to PoseKeypointType.RIGHT_SHOULDER,
        PoseKeypointType.LEFT_ELBOW to PoseKeypointType.RIGHT_ELBOW,
        PoseKeypointType.LEFT_WRIST to PoseKeypointType.RIGHT_WRIST,
        PoseKeypointType.LEFT_HIP to PoseKeypointType.RIGHT_HIP,
        PoseKeypointType.LEFT_KNEE to PoseKeypointType.RIGHT_KNEE,
        PoseKeypointType.LEFT_ANKLE to PoseKeypointType.RIGHT_ANKLE,
        PoseKeypointType.LEFT_EYE to PoseKeypointType.RIGHT_EYE,
        PoseKeypointType.LEFT_EAR to PoseKeypointType.RIGHT_EAR,
        PoseKeypointType.LEFT_HEEL to PoseKeypointType.RIGHT_HEEL,
        PoseKeypointType.LEFT_FOOT_INDEX to PoseKeypointType.RIGHT_FOOT_INDEX,
        PoseKeypointType.LEFT_THUMB to PoseKeypointType.RIGHT_THUMB,
        PoseKeypointType.LEFT_INDEX to PoseKeypointType.RIGHT_INDEX,
        PoseKeypointType.LEFT_PINKY to PoseKeypointType.RIGHT_PINKY,
    )
    pairs.forEach { (left, right) ->
        put(left, right)
        put(right, left)
    }
    put(PoseKeypointType.HEAD, PoseKeypointType.HEAD)
    put(PoseKeypointType.NECK, PoseKeypointType.NECK)
}

/**
 * Stage 24: the pose a viewer would see if [snapshot] (extracted from the *un-flipped* reference
 * photo - pose detection is never re-run just for a flip) were mirrored the same way the
 * reference image is on screen. Every point's x position is reflected (`1 - xNorm`), and each
 * LEFT_*/RIGHT_* keypoint swaps places with its counterpart, since mirroring a photo swaps which
 * side of the body appears on which side of the frame. Used only to build the reference side of
 * a pose comparison when the overlay's flip is on ([OverlayState.flipped]); the stored
 * [PoseDetectionResult] itself is never mutated, so turning flip off is instant.
 */
fun mirrorPoseSnapshot(snapshot: PoseSnapshot): PoseSnapshot {
    val mirrored = snapshot.keypoints.entries.associate { (type, point) ->
        val mirroredType = MIRRORED_TYPE[type] ?: type
        mirroredType to point.copy(type = mirroredType, xNorm = 1f - point.xNorm)
    }
    return snapshot.copy(keypoints = mirrored)
}

/** Outcome of trying to detect a pose in one photo. */
enum class PoseDetectionStatus {
    /** A usable pose was found; [PoseDetectionResult.poses] is non-empty. */
    SUCCESS,
    /** No human body was found in the image. */
    NO_PERSON_DETECTED,
    /** A body was found, but too few core keypoints were confident enough to trust the pose
     *  (e.g. the body is heavily cropped or out of frame). The partial data is still attached,
     *  so a later stage can decide for itself whether it is still useful for anything. */
    LOW_CONFIDENCE,
    /** The detector/image pipeline itself failed (corrupt bitmap, decoder error, etc.). */
    FAILED,
}

/**
 * Result of running pose detection on one reference photo.
 *
 * [poses] is a list - not a single nullable pose - purely so a future stage that can tell
 * multiple people apart (e.g. by first running person/object detection) can attach more than
 * one [PoseSnapshot] here without another data-model change. Only one entry is ever produced
 * today; when several people appear in the photo, the model simply picks the most prominent
 * one, same as it would for a single person image.
 */
data class PoseDetectionResult(
    val status: PoseDetectionStatus,
    val poses: List<PoseSnapshot> = emptyList(),
) {
    val isValid: Boolean get() = status == PoseDetectionStatus.SUCCESS && poses.isNotEmpty()
    val primaryPose: PoseSnapshot? get() = poses.firstOrNull()
}
