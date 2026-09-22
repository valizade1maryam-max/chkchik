package com.chikchik.posecamera.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Stage 12: compares a [PoseSnapshot] extracted from a reference photo (Stage 10) against a
 * [PoseSnapshot] detected live from the camera (Stage 11).
 *
 * This file only builds the comparison *engine* and its output data model - it does not draw
 * anything on screen and does not turn a difference into a spoken/written instruction ("raise
 * your arm", scoring, step-by-step guidance). That is Stage 13; this file's [PoseComparisonResult]
 * is designed so Stage 13 can be built entirely on top of it.
 *
 * Two complementary signals are combined, exactly as the spec asks for:
 *  - **Position**: each keypoint is compared in a normalized space (translated to the torso
 *    center, scaled by torso length) so neither the user's distance from the camera nor where
 *    they stand in the frame affects the result on its own.
 *  - **Angle**: limb angles are measured *relative to the pose's own torso axis* (not the
 *    screen), so two people standing at different spots, angles, or distances from the camera
 *    but holding the same pose still compare as similar. Joint bend angles (elbow/knee) are
 *    the interior angle between two bones, which is naturally position/scale/rotation
 *    invariant. The one exception is [AngleType.TORSO_LEAN], which is intentionally measured
 *    against the screen's vertical axis since the overall lean/tilt of the body is itself part
 *    of a pose - see the "known limitations" note near [torsoUpAngleDegrees].
 *
 * Every measurement degrades to "unknown" rather than a guessed number whenever a keypoint's
 * detection confidence is too low on either side, per the Stage 12 spec's confidence rule.
 */

// ---------------------------------------------------------------------------------------------
// Output data model
// ---------------------------------------------------------------------------------------------

/** Coarse body regions the spec asks for a separate difference on. */
enum class BodyPart {
    HEAD, LEFT_ARM, RIGHT_ARM, TORSO, LEFT_LEG, RIGHT_LEG,
}

/** Named limb/joint angles compared between the two poses. */
enum class AngleType {
    /** Direction the head/neck points, relative to the torso axis. */
    HEAD_DIRECTION,
    /** Lean/tilt of the torso itself, relative to the screen's vertical axis. */
    TORSO_LEAN,
    LEFT_UPPER_ARM, RIGHT_UPPER_ARM,
    LEFT_FOREARM, RIGHT_FOREARM,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_THIGH, RIGHT_THIGH,
    LEFT_SHIN, RIGHT_SHIN,
    LEFT_KNEE, RIGHT_KNEE,
}

/**
 * How different one measurement (a keypoint, an angle, or a whole body part) is between the
 * reference and live pose. [UNKNOWN] means there was not enough confident data on one or both
 * sides to judge it at all - never a guess.
 */
enum class DifferenceStatus { MATCH, MINOR, MODERATE, MAJOR, UNKNOWN }

/** Comparison of a single keypoint's position, in the normalized (translation+scale-free) space. */
data class KeypointDifference(
    val type: PoseKeypointType,
    val referenceAvailable: Boolean,
    val liveAvailable: Boolean,
    /** Distance between the two normalized positions, in "torso length" units. Null = unknown. */
    val positionDifference: Float?,
    val status: DifferenceStatus,
)

/** Comparison of a single named angle between the reference and live pose. */
data class AngleComparison(
    val type: AngleType,
    val referenceAngleDegrees: Float?,
    val userAngleDegrees: Float?,
    val angleDifferenceDegrees: Float?,
    val status: DifferenceStatus,
)

/** Aggregated result for one [BodyPart], built from whichever of its keypoints/angles were usable. */
data class BodyPartComparison(
    val part: BodyPart,
    /** 0f (identical) upwards; null when nothing in this part could be confidently compared. */
    val differenceScore: Float?,
    val status: DifferenceStatus,
    val keypoints: List<PoseKeypointType>,
    val angles: List<AngleType>,
)

/**
 * Full result of comparing one reference pose to one live pose. This is the single object a
 * later stage (text guidance, coaching UI, scoring) can be built from without needing to touch
 * the comparison math itself.
 */
data class PoseComparisonResult(
    val overallDifference: Float?,
    val overallStatus: DifferenceStatus,
    val head: BodyPartComparison,
    val leftArm: BodyPartComparison,
    val rightArm: BodyPartComparison,
    val torso: BodyPartComparison,
    val leftLeg: BodyPartComparison,
    val rightLeg: BodyPartComparison,
    /** Every keypoint present in either pose (not just the core 14) - lets a future stage with
     *  extra keypoints (face, fingers, feet) reuse this without another data-model change. */
    val keypoints: Map<PoseKeypointType, KeypointDifference>,
    val angles: Map<AngleType, AngleComparison>,
    val referenceConfidence: Float,
    val liveConfidence: Float,
) {
    /** All six body parts, for callers that want to iterate instead of naming each field. */
    val parts: List<BodyPartComparison> get() = listOf(head, leftArm, rightArm, torso, leftLeg, rightLeg)
}

// ---------------------------------------------------------------------------------------------
// Thresholds - all the "how different is different" heuristics live here, in one place.
// ---------------------------------------------------------------------------------------------

/** Position difference (in torso-length units) below which two points count as effectively the same. */
private const val POSITION_MATCH = 0.15f
private const val POSITION_MINOR = 0.30f
private const val POSITION_MODERATE = 0.50f
/** Position difference at/above which severity saturates to 1f (fully "major"). */
private const val POSITION_SEVERITY_SCALE = 0.65f

private const val ANGLE_MATCH_DEG = 15f
private const val ANGLE_MINOR_DEG = 30f
private const val ANGLE_MODERATE_DEG = 60f
/** Angle difference at/above which severity saturates to 1f (fully "major"). */
private const val ANGLE_SEVERITY_SCALE_DEG = 90f

/** A torso scale below this (normalized units) is treated as a degenerate detection - too small
 *  to safely divide by - rather than trusted. */
private const val MIN_TORSO_SCALE = 0.02f

// ---------------------------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------------------------

/**
 * Compares [reference] (from the picked reference photo) against [live] (from the current
 * camera frame). Pure/cheap math over at most ~15-20 points - safe to call on every throttled
 * live-pose update ([LivePoseAnalyzer] already limits how often that happens) without affecting
 * preview/shutter performance.
 */
fun comparePoses(reference: PoseSnapshot, live: PoseSnapshot): PoseComparisonResult {
    val allTypes = (reference.keypoints.keys + live.keypoints.keys).distinct()
    val keypointDiffs = allTypes.associateWith { type -> compareKeypoint(type, reference, live) }
    val angleDiffs = AngleType.entries.associateWith { type -> compareAngle(type, reference, live) }

    val head = aggregatePart(BodyPart.HEAD, listOf(PoseKeypointType.HEAD, PoseKeypointType.NECK),
        listOf(AngleType.HEAD_DIRECTION), keypointDiffs, angleDiffs)
    val leftArm = aggregatePart(BodyPart.LEFT_ARM,
        listOf(PoseKeypointType.LEFT_SHOULDER, PoseKeypointType.LEFT_ELBOW, PoseKeypointType.LEFT_WRIST),
        listOf(AngleType.LEFT_UPPER_ARM, AngleType.LEFT_FOREARM, AngleType.LEFT_ELBOW), keypointDiffs, angleDiffs)
    val rightArm = aggregatePart(BodyPart.RIGHT_ARM,
        listOf(PoseKeypointType.RIGHT_SHOULDER, PoseKeypointType.RIGHT_ELBOW, PoseKeypointType.RIGHT_WRIST),
        listOf(AngleType.RIGHT_UPPER_ARM, AngleType.RIGHT_FOREARM, AngleType.RIGHT_ELBOW), keypointDiffs, angleDiffs)
    val torso = aggregatePart(BodyPart.TORSO,
        listOf(PoseKeypointType.NECK, PoseKeypointType.LEFT_SHOULDER, PoseKeypointType.RIGHT_SHOULDER,
            PoseKeypointType.LEFT_HIP, PoseKeypointType.RIGHT_HIP),
        listOf(AngleType.TORSO_LEAN), keypointDiffs, angleDiffs)
    val leftLeg = aggregatePart(BodyPart.LEFT_LEG,
        listOf(PoseKeypointType.LEFT_HIP, PoseKeypointType.LEFT_KNEE, PoseKeypointType.LEFT_ANKLE),
        listOf(AngleType.LEFT_THIGH, AngleType.LEFT_SHIN, AngleType.LEFT_KNEE), keypointDiffs, angleDiffs)
    val rightLeg = aggregatePart(BodyPart.RIGHT_LEG,
        listOf(PoseKeypointType.RIGHT_HIP, PoseKeypointType.RIGHT_KNEE, PoseKeypointType.RIGHT_ANKLE),
        listOf(AngleType.RIGHT_THIGH, AngleType.RIGHT_SHIN, AngleType.RIGHT_KNEE), keypointDiffs, angleDiffs)

    val parts = listOf(head, leftArm, rightArm, torso, leftLeg, rightLeg)
    val partScores = parts.mapNotNull { it.differenceScore }
    val overallDifference = if (partScores.isEmpty()) null else partScores.average().toFloat()
    val overallStatus = overallDifference?.let(::statusForSeverity) ?: DifferenceStatus.UNKNOWN

    return PoseComparisonResult(
        overallDifference = overallDifference,
        overallStatus = overallStatus,
        head = head, leftArm = leftArm, rightArm = rightArm,
        torso = torso, leftLeg = leftLeg, rightLeg = rightLeg,
        keypoints = keypointDiffs,
        angles = angleDiffs,
        referenceConfidence = reference.overallConfidence,
        liveConfidence = live.overallConfidence,
    )
}

/** Convenience overload for optional snapshots (e.g. straight from two [PoseDetectionResult]s) -
 *  returns null, rather than a half-built result, whenever either side has no usable pose yet.
 *  Named differently (not an overload of [comparePoses]) because a nullable/non-null parameter
 *  pair with otherwise identical types erases to the same JVM signature and the platform
 *  rejects it as a clash. */
fun comparePosesOrNull(reference: PoseSnapshot?, live: PoseSnapshot?): PoseComparisonResult? {
    if (reference == null || live == null) return null
    return comparePoses(reference, live)
}

// ---------------------------------------------------------------------------------------------
// Keypoint (position) comparison
// ---------------------------------------------------------------------------------------------

private fun compareKeypoint(type: PoseKeypointType, reference: PoseSnapshot, live: PoseSnapshot): KeypointDifference {
    val refPoint = reference.keypoint(type)
    val livePoint = live.keypoint(type)
    val refAvailable = refPoint != null && refPoint.confidence >= MIN_KEYPOINT_CONFIDENCE
    val liveAvailable = livePoint != null && livePoint.confidence >= MIN_KEYPOINT_CONFIDENCE

    if (!refAvailable || !liveAvailable || refPoint == null || livePoint == null) {
        return KeypointDifference(type, refAvailable, liveAvailable, null, DifferenceStatus.UNKNOWN)
    }

    val refAnchor = torsoAnchor(reference)
    val liveAnchor = torsoAnchor(live)
    val refScale = torsoScale(reference)
    val liveScale = torsoScale(live)
    if (refAnchor == null || liveAnchor == null ||
        refScale == null || refScale < MIN_TORSO_SCALE ||
        liveScale == null || liveScale < MIN_TORSO_SCALE
    ) {
        // Not enough of the body detected on one side to build a reliable normalized space -
        // report the point itself as available, but the comparison as unknown, per spec.
        return KeypointDifference(type, true, true, null, DifferenceStatus.UNKNOWN)
    }

    val refX = (refPoint.xNorm - refAnchor.first) / refScale
    val refY = (refPoint.yNorm - refAnchor.second) / refScale
    val liveX = (livePoint.xNorm - liveAnchor.first) / liveScale
    val liveY = (livePoint.yNorm - liveAnchor.second) / liveScale
    val diff = distance(refX, refY, liveX, liveY)
    return KeypointDifference(type, true, true, diff, statusForPosition(diff))
}

/** Torso center (hip midpoint, falling back to shoulder midpoint) used as the origin that
 *  position comparisons are translated to, so where the person stands in frame doesn't matter. */
private fun torsoAnchor(pose: PoseSnapshot): Pair<Float, Float>? {
    val leftHip = pose.keypoint(PoseKeypointType.LEFT_HIP)?.takeIfConfident()
    val rightHip = pose.keypoint(PoseKeypointType.RIGHT_HIP)?.takeIfConfident()
    if (leftHip != null && rightHip != null) {
        return Pair((leftHip.xNorm + rightHip.xNorm) / 2f, (leftHip.yNorm + rightHip.yNorm) / 2f)
    }
    val leftShoulder = pose.keypoint(PoseKeypointType.LEFT_SHOULDER)?.takeIfConfident()
    val rightShoulder = pose.keypoint(PoseKeypointType.RIGHT_SHOULDER)?.takeIfConfident()
    if (leftShoulder != null && rightShoulder != null) {
        return Pair((leftShoulder.xNorm + rightShoulder.xNorm) / 2f, (leftShoulder.yNorm + rightShoulder.yNorm) / 2f)
    }
    return null
}

/** Torso length (neck to hip midpoint), used as the unit that position comparisons are scaled
 *  by, so the user's distance from the camera doesn't matter. Falls back to shoulder width
 *  (roughly proportional to torso length for a human body) when the hips aren't visible. */
private fun torsoScale(pose: PoseSnapshot): Float? {
    val neck = pose.keypoint(PoseKeypointType.NECK)?.takeIfConfident()
    val anchor = torsoAnchor(pose)
    if (neck != null && anchor != null) {
        val d = distance(neck.xNorm, neck.yNorm, anchor.first, anchor.second)
        if (d >= MIN_TORSO_SCALE) return d
    }
    val leftShoulder = pose.keypoint(PoseKeypointType.LEFT_SHOULDER)?.takeIfConfident()
    val rightShoulder = pose.keypoint(PoseKeypointType.RIGHT_SHOULDER)?.takeIfConfident()
    if (leftShoulder != null && rightShoulder != null) {
        // A shoulder-width-only fallback; roughly torso-length-proportional for a typical body,
        // just used as a last resort when hips are out of frame.
        val shoulderWidth = distance(leftShoulder.xNorm, leftShoulder.yNorm, rightShoulder.xNorm, rightShoulder.yNorm)
        return shoulderWidth * 1.3f
    }
    return null
}

private fun PoseKeypoint.takeIfConfident(): PoseKeypoint? = if (confidence >= MIN_KEYPOINT_CONFIDENCE) this else null

private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
}

// ---------------------------------------------------------------------------------------------
// Angle comparison
// ---------------------------------------------------------------------------------------------

private enum class AngleKind { INTERIOR, CIRCULAR }

/** How to compute one pose's value for an [AngleType], and how its diff should be measured. */
private class AngleSpec(val kind: AngleKind, val compute: (PoseSnapshot) -> Float?)

private val ANGLE_SPECS: Map<AngleType, AngleSpec> = mapOf(
    AngleType.HEAD_DIRECTION to AngleSpec(AngleKind.CIRCULAR) {
        limbAngleRelativeToTorso(it, PoseKeypointType.NECK, PoseKeypointType.HEAD)
    },
    AngleType.TORSO_LEAN to AngleSpec(AngleKind.CIRCULAR) { torsoUpAngleDegrees(it) },
    AngleType.LEFT_UPPER_ARM to AngleSpec(AngleKind.CIRCULAR) {
        limbAngleRelativeToTorso(it, PoseKeypointType.LEFT_SHOULDER, PoseKeypointType.LEFT_ELBOW)
    },
    AngleType.RIGHT_UPPER_ARM to AngleSpec(AngleKind.CIRCULAR) {
        limbAngleRelativeToTorso(it, PoseKeypointType.RIGHT_SHOULDER, PoseKeypointType.RIGHT_ELBOW)
    },
    AngleType.LEFT_FOREARM to AngleSpec(AngleKind.CIRCULAR) {
        limbAngleRelativeToTorso(it, PoseKeypointType.LEFT_ELBOW, PoseKeypointType.LEFT_WRIST)
    },
    AngleType.RIGHT_FOREARM to AngleSpec(AngleKind.CIRCULAR) {
        limbAngleRelativeToTorso(it, PoseKeypointType.RIGHT_ELBOW, PoseKeypointType.RIGHT_WRIST)
    },
    AngleType.LEFT_ELBOW to AngleSpec(AngleKind.INTERIOR) {
        interiorAngle(it, PoseKeypointType.LEFT_SHOULDER, PoseKeypointType.LEFT_ELBOW, PoseKeypointType.LEFT_WRIST)
    },
    AngleType.RIGHT_ELBOW to AngleSpec(AngleKind.INTERIOR) {
        interiorAngle(it, PoseKeypointType.RIGHT_SHOULDER, PoseKeypointType.RIGHT_ELBOW, PoseKeypointType.RIGHT_WRIST)
    },
    AngleType.LEFT_THIGH to AngleSpec(AngleKind.CIRCULAR) {
        limbAngleRelativeToTorso(it, PoseKeypointType.LEFT_HIP, PoseKeypointType.LEFT_KNEE)
    },
    AngleType.RIGHT_THIGH to AngleSpec(AngleKind.CIRCULAR) {
        limbAngleRelativeToTorso(it, PoseKeypointType.RIGHT_HIP, PoseKeypointType.RIGHT_KNEE)
    },
    AngleType.LEFT_SHIN to AngleSpec(AngleKind.CIRCULAR) {
        limbAngleRelativeToTorso(it, PoseKeypointType.LEFT_KNEE, PoseKeypointType.LEFT_ANKLE)
    },
    AngleType.RIGHT_SHIN to AngleSpec(AngleKind.CIRCULAR) {
        limbAngleRelativeToTorso(it, PoseKeypointType.RIGHT_KNEE, PoseKeypointType.RIGHT_ANKLE)
    },
    AngleType.LEFT_KNEE to AngleSpec(AngleKind.INTERIOR) {
        interiorAngle(it, PoseKeypointType.LEFT_HIP, PoseKeypointType.LEFT_KNEE, PoseKeypointType.LEFT_ANKLE)
    },
    AngleType.RIGHT_KNEE to AngleSpec(AngleKind.INTERIOR) {
        interiorAngle(it, PoseKeypointType.RIGHT_HIP, PoseKeypointType.RIGHT_KNEE, PoseKeypointType.RIGHT_ANKLE)
    },
)

private fun compareAngle(type: AngleType, reference: PoseSnapshot, live: PoseSnapshot): AngleComparison {
    val spec = ANGLE_SPECS.getValue(type)
    val refAngle = spec.compute(reference)
    val liveAngle = spec.compute(live)
    if (refAngle == null || liveAngle == null) {
        return AngleComparison(type, refAngle, liveAngle, null, DifferenceStatus.UNKNOWN)
    }
    val diff = when (spec.kind) {
        AngleKind.INTERIOR -> abs(refAngle - liveAngle)
        AngleKind.CIRCULAR -> circularAngleDiff(refAngle, liveAngle)
    }
    return AngleComparison(type, refAngle, liveAngle, diff, statusForAngle(diff))
}

/**
 * Direction of the vector from [from] to [to], measured relative to the pose's own torso axis
 * (straight "up" the body, hip-midpoint to neck) rather than the screen. This is what makes limb
 * angles comparable between a reference and a live user even when the two bodies are rotated,
 * shifted, or scaled differently in their own frames - only returns a value when every point
 * involved (including the torso itself) is confidently detected on this side.
 */
private fun limbAngleRelativeToTorso(pose: PoseSnapshot, from: PoseKeypointType, to: PoseKeypointType): Float? {
    val torsoAngle = torsoUpAngleDegrees(pose) ?: return null
    val a = pose.keypoint(from)?.takeIfConfident() ?: return null
    val b = pose.keypoint(to)?.takeIfConfident() ?: return null
    val limbAngle = angleOfVector(b.xNorm - a.xNorm, b.yNorm - a.yNorm)
    return normalizeSigned(limbAngle - torsoAngle)
}

/**
 * Direction of the torso itself (hip midpoint -> neck), measured against the screen's vertical
 * axis. Unlike every other angle above, this one is *not* relative-to-self - it IS the
 * reference axis the others are measured against, so there is nothing left to make it relative
 * to. That also means, unlike the others, [AngleType.TORSO_LEAN] is sensitive to the camera
 * being rotated/tilted differently between the reference photo and the live feed; documented as
 * a known limitation of this stage rather than solved here (see the summary at the end).
 */
private fun torsoUpAngleDegrees(pose: PoseSnapshot): Float? {
    val neck = pose.keypoint(PoseKeypointType.NECK)?.takeIfConfident() ?: return null
    val anchor = torsoAnchor(pose) ?: return null
    return angleOfVector(neck.xNorm - anchor.first, neck.yNorm - anchor.second)
}

/** Interior angle at [joint], between the rays joint->[a] and joint->[b]; 0..180 degrees, and
 *  naturally invariant to position, scale, and rotation - used for the elbow/knee bend itself. */
private fun interiorAngle(pose: PoseSnapshot, a: PoseKeypointType, joint: PoseKeypointType, b: PoseKeypointType): Float? {
    val pa = pose.keypoint(a)?.takeIfConfident() ?: return null
    val pj = pose.keypoint(joint)?.takeIfConfident() ?: return null
    val pb = pose.keypoint(b)?.takeIfConfident() ?: return null
    val v1x = pa.xNorm - pj.xNorm
    val v1y = pa.yNorm - pj.yNorm
    val v2x = pb.xNorm - pj.xNorm
    val v2y = pb.yNorm - pj.yNorm
    val mag1 = sqrt(v1x * v1x + v1y * v1y)
    val mag2 = sqrt(v2x * v2x + v2y * v2y)
    if (mag1 < 1e-6f || mag2 < 1e-6f) return null
    val cos = ((v1x * v2x + v1y * v2y) / (mag1 * mag2)).coerceIn(-1f, 1f)
    return Math.toDegrees(acos(cos.toDouble())).toFloat()
}

/** Angle of vector (dx, dy) in degrees, 0deg = pointing straight up the screen, increasing
 *  clockwise. Only the relative differences between two such angles are ever used above. */
private fun angleOfVector(dx: Float, dy: Float): Float =
    Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()

/** Wraps a degree value into (-180, 180]. */
private fun normalizeSigned(angle: Float): Float {
    var a = angle % 360f
    if (a > 180f) a -= 360f
    if (a <= -180f) a += 360f
    return a
}

/** Smallest angle between two directions, 0..180 degrees - handles wraparound (e.g. 179 vs -179
 *  degrees are 2 degrees apart, not 358). */
private fun circularAngleDiff(a: Float, b: Float): Float = abs(normalizeSigned(a - b))

// ---------------------------------------------------------------------------------------------
// Status/severity helpers shared by keypoints, angles, and body-part aggregation
// ---------------------------------------------------------------------------------------------

private fun statusForPosition(diff: Float): DifferenceStatus = when {
    diff < POSITION_MATCH -> DifferenceStatus.MATCH
    diff < POSITION_MINOR -> DifferenceStatus.MINOR
    diff < POSITION_MODERATE -> DifferenceStatus.MODERATE
    else -> DifferenceStatus.MAJOR
}

private fun statusForAngle(diff: Float): DifferenceStatus = when {
    diff < ANGLE_MATCH_DEG -> DifferenceStatus.MATCH
    diff < ANGLE_MINOR_DEG -> DifferenceStatus.MINOR
    diff < ANGLE_MODERATE_DEG -> DifferenceStatus.MODERATE
    else -> DifferenceStatus.MAJOR
}

/** Maps an aggregated 0f..1f severity score (see [aggregatePart]) back to a [DifferenceStatus]. */
private fun statusForSeverity(severity: Float): DifferenceStatus = when {
    severity < 0.25f -> DifferenceStatus.MATCH
    severity < 0.5f -> DifferenceStatus.MINOR
    severity < 0.75f -> DifferenceStatus.MODERATE
    else -> DifferenceStatus.MAJOR
}

/**
 * Combines whichever of [keypointTypes]/[angleTypes] were confidently comparable into one
 * [BodyPartComparison]. Each contributing measurement is first normalized to a 0f..1f severity
 * (position/[POSITION_SEVERITY_SCALE], angle/[ANGLE_SEVERITY_SCALE_DEG]) so position and angle
 * differences - which are in unrelated units - can be averaged together meaningfully. A part
 * with nothing confidently measurable on either side comes back as [DifferenceStatus.UNKNOWN]
 * rather than a misleading zero.
 */
private fun aggregatePart(
    part: BodyPart,
    keypointTypes: List<PoseKeypointType>,
    angleTypes: List<AngleType>,
    keypointDiffs: Map<PoseKeypointType, KeypointDifference>,
    angleDiffs: Map<AngleType, AngleComparison>,
): BodyPartComparison {
    val severities = mutableListOf<Float>()
    for (type in keypointTypes) {
        val diff = keypointDiffs[type]?.positionDifference ?: continue
        severities += (diff / POSITION_SEVERITY_SCALE).coerceIn(0f, 1f)
    }
    for (type in angleTypes) {
        val diff = angleDiffs[type]?.angleDifferenceDegrees ?: continue
        severities += (diff / ANGLE_SEVERITY_SCALE_DEG).coerceIn(0f, 1f)
    }
    val score = if (severities.isEmpty()) null else severities.average().toFloat()
    val status = score?.let(::statusForSeverity) ?: DifferenceStatus.UNKNOWN
    return BodyPartComparison(part, score, status, keypointTypes, angleTypes)
}
