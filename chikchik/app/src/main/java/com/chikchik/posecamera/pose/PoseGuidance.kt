package com.chikchik.posecamera.pose

/**
 * Stage 13: turns a Stage 12 [PoseComparisonResult] into a single, plain-language instruction the
 * user can follow without staring at the skeleton overlay ("raise your right hand", "turn your
 * head a bit left", ...).
 *
 * This file is pure Kotlin - no Compose, no Android `Context`, no hardcoded Persian text - exactly
 * like [PoseComparison.kt][comparePoses] before it. An instruction is returned as a
 * [GuidanceInstruction] value describing *which* limb and *which* direction; the actual sentence
 * is assembled from string resources at the UI layer (see `PoseGuidanceBar.kt`), the same way
 * every other piece of user-facing text in this app already works.
 *
 * Everything here is derived directly from the real position/angle differences Stage 12 already
 * computed - never a random guess or a fixed/canned message - and only when the underlying
 * keypoints were confidently detected on both sides, per the Stage 13 spec.
 *
 * ## A note on left/right and camera mirroring (spec requirement #7)
 * [PoseKeypointType.LEFT_WRIST] / [PoseKeypointType.RIGHT_WRIST] etc. already refer to the
 * person's own anatomical left/right, independent of front/back camera: [LivePoseAnalyzer] hands
 * ML Kit the *raw, un-mirrored* sensor frame (only [ui.camera.PoseOverlay]'s on-screen drawing
 * mirrors the front camera to match the mirrored preview - the underlying keypoint data never
 * does). This file only ever reasons about keypoint *types* (`LEFT_WRIST` vs `RIGHT_WRIST`) and
 * normalized position/angle deltas from [PoseComparisonResult] - never about screen/preview pixel
 * positions or `lensFacing` - so a mirrored front-camera preview can never flip an instruction.
 *
 * A raw (un-mirrored) camera frame always shows a person the same way a photo of someone facing
 * you does: their real right hand appears on the *left* side of the frame (smaller `xNorm`), the
 * same "facing a mirror" inversion everyone already knows from real life. [directionOfDelta]
 * below is the one place that fact is used, to turn a plain image-space position delta into a
 * "move to your own left/right" instruction.
 */

// ---------------------------------------------------------------------------------------------
// Output data model
// ---------------------------------------------------------------------------------------------

/** Direction a limb should move, or a head/torso should turn, to get closer to the reference. */
enum class GuidanceDirection { UP, DOWN, LEFT, RIGHT, ROTATE_LEFT, ROTATE_RIGHT }

/**
 * Which named limb/region an instruction talks about - finer-grained than [BodyPart] so a
 * sentence can say "hand" vs "elbow" vs "leg", matching the Stage 13 spec's own examples.
 */
enum class GuidanceLimb {
    HEAD, TORSO,
    LEFT_HAND, RIGHT_HAND,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_LEG, RIGHT_LEG,
}

/** Why a specific instruction could not be given, per spec requirement #8. */
enum class LowConfidenceReason {
    /** No live pose detected at all (person not visible / camera just switched). */
    NO_PERSON,
    /** A pose is detected, but core points (esp. head/feet) are missing in a way that looks like
     *  the body doesn't fully fit in the frame - most likely because the user is too close. */
    LIKELY_TOO_CLOSE,
    /** A pose is detected but overall too uncertain to safely guide from (poor light, heavy
     *  motion blur, mostly out of frame, ...) without a more specific reason to point to. */
    POORLY_FRAMED,
}

/** One thing to show the user right now. */
sealed class GuidanceInstruction {
    /** Move/rotate [limb] in [direction]; the single most important correction right now. */
    data class Correct(val limb: GuidanceLimb, val direction: GuidanceDirection) : GuidanceInstruction()

    /** [limb] was just corrected - shown briefly before the next instruction appears. */
    data class Corrected(val limb: GuidanceLimb) : GuidanceInstruction()

    /** Overall pose difference is within the acceptable range - nothing left to correct. */
    data object PoseReady : GuidanceInstruction()

    /** Not enough confident data to safely guide right now; see [reason]. */
    data class LowConfidence(val reason: LowConfidenceReason) : GuidanceInstruction()

    /** No reference pose / no live pose to compare at all - nothing to show. */
    data object None : GuidanceInstruction()
}

// ---------------------------------------------------------------------------------------------
// Thresholds
// ---------------------------------------------------------------------------------------------

/** How long a [GuidanceInstruction.Corrected] stays on screen before moving on to the next
 *  correction, in milliseconds. Long enough to read a short phrase, short enough to not stall
 *  guidance toward the next part. */
private const val CORRECTED_DISPLAY_MS = 1400L

/** Below this live-pose confidence, don't attempt specific per-limb guidance at all (spec #8) -
 *  show a general framing/distance hint instead, never a confident-sounding limb instruction
 *  built on shaky data. */
private const val MIN_LIVE_CONFIDENCE_FOR_GUIDANCE = 0.35f

// ---------------------------------------------------------------------------------------------
// Stateless part -> instruction resolution
// ---------------------------------------------------------------------------------------------

/** The six comparable body parts, in a fixed tie-break order used only when two parts are
 *  equally severe (arms first - most expressive/visible - then head, torso, legs). */
private val PART_PRIORITY: List<BodyPart> = listOf(
    BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM,
    BodyPart.HEAD, BodyPart.TORSO,
    BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG,
)

/**
 * Turns an image-space position delta (reference minus live, in Stage 12's normalized,
 * translation/scale-free space - same axes as the raw image, never rotated) into an up/down/
 * left/right direction. See the file-level doc comment for why smaller `x` = the person's own
 * right and larger `x` = the person's own left.
 */
private fun directionOfDelta(dx: Float, dy: Float): GuidanceDirection =
    if (kotlin.math.abs(dy) >= kotlin.math.abs(dx)) {
        if (dy < 0f) GuidanceDirection.UP else GuidanceDirection.DOWN
    } else {
        if (dx < 0f) GuidanceDirection.RIGHT else GuidanceDirection.LEFT
    }

/** Same idea as [directionOfDelta], but for a signed angle difference (reference minus live,
 *  degrees, same convention as [AngleComparison]: 0deg = straight up, increasing clockwise in
 *  raw image space) -> a rotation instruction. See the file-level doc comment for the left/right
 *  reasoning; it is the same underlying fact, just applied to an angle instead of a position. */
private fun rotationOfDelta(deltaDegrees: Float): GuidanceDirection =
    if (deltaDegrees > 0f) GuidanceDirection.ROTATE_LEFT else GuidanceDirection.ROTATE_RIGHT

/** Recomputes the signed, normalized (reference-minus-live) position delta for one keypoint type,
 *  in the exact same translation/scale-normalized space Stage 12 uses for [KeypointDifference] -
 *  needed here because that struct only kept the scalar distance, not its direction. Returns null
 *  whenever Stage 12 itself would have reported this point as [DifferenceStatus.UNKNOWN]. */
private fun signedKeypointDelta(
    type: PoseKeypointType,
    reference: PoseSnapshot,
    live: PoseSnapshot,
): Pair<Float, Float>? {
    val refPoint = reference.keypoint(type) ?: return null
    val livePoint = live.keypoint(type) ?: return null
    if (refPoint.confidence < MIN_KEYPOINT_CONFIDENCE || livePoint.confidence < MIN_KEYPOINT_CONFIDENCE) return null
    val refAnchor = torsoAnchorFor(reference) ?: return null
    val liveAnchor = torsoAnchorFor(live) ?: return null
    val refScale = torsoScaleFor(reference)
    val liveScale = torsoScaleFor(live)
    if (refScale == null || refScale < MIN_TORSO_SCALE_FOR_GUIDANCE ||
        liveScale == null || liveScale < MIN_TORSO_SCALE_FOR_GUIDANCE
    ) return null
    val refX = (refPoint.xNorm - refAnchor.first) / refScale
    val refY = (refPoint.yNorm - refAnchor.second) / refScale
    val liveX = (livePoint.xNorm - liveAnchor.first) / liveScale
    val liveY = (livePoint.yNorm - liveAnchor.second) / liveScale
    return Pair(refX - liveX, refY - liveY)
}

/** Mirrors [POSITION_MATCH]-style thresholding from PoseComparison.kt: a delta this small is
 *  noise, not a real difference worth instructing on. Kept separate from Stage 12's own constant
 *  so this file has no dependency on PoseComparison.kt's private thresholds. */
private const val MIN_MEANINGFUL_POSITION_DELTA = 0.15f
private const val MIN_TORSO_SCALE_FOR_GUIDANCE = 0.02f

private fun torsoAnchorFor(pose: PoseSnapshot): Pair<Float, Float>? {
    val leftHip = pose.keypoint(PoseKeypointType.LEFT_HIP)?.takeIfConfidentForGuidance()
    val rightHip = pose.keypoint(PoseKeypointType.RIGHT_HIP)?.takeIfConfidentForGuidance()
    if (leftHip != null && rightHip != null) {
        return Pair((leftHip.xNorm + rightHip.xNorm) / 2f, (leftHip.yNorm + rightHip.yNorm) / 2f)
    }
    val leftShoulder = pose.keypoint(PoseKeypointType.LEFT_SHOULDER)?.takeIfConfidentForGuidance()
    val rightShoulder = pose.keypoint(PoseKeypointType.RIGHT_SHOULDER)?.takeIfConfidentForGuidance()
    if (leftShoulder != null && rightShoulder != null) {
        return Pair((leftShoulder.xNorm + rightShoulder.xNorm) / 2f, (leftShoulder.yNorm + rightShoulder.yNorm) / 2f)
    }
    return null
}

private fun torsoScaleFor(pose: PoseSnapshot): Float? {
    val neck = pose.keypoint(PoseKeypointType.NECK)?.takeIfConfidentForGuidance()
    val anchor = torsoAnchorFor(pose)
    if (neck != null && anchor != null) {
        val dx = neck.xNorm - anchor.first
        val dy = neck.yNorm - anchor.second
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        if (d >= MIN_TORSO_SCALE_FOR_GUIDANCE) return d
    }
    val leftShoulder = pose.keypoint(PoseKeypointType.LEFT_SHOULDER)?.takeIfConfidentForGuidance()
    val rightShoulder = pose.keypoint(PoseKeypointType.RIGHT_SHOULDER)?.takeIfConfidentForGuidance()
    if (leftShoulder != null && rightShoulder != null) {
        val dx = leftShoulder.xNorm - rightShoulder.xNorm
        val dy = leftShoulder.yNorm - rightShoulder.yNorm
        return kotlin.math.sqrt(dx * dx + dy * dy) * 1.3f
    }
    return null
}

private fun PoseKeypoint.takeIfConfidentForGuidance(): PoseKeypoint? =
    if (confidence >= MIN_KEYPOINT_CONFIDENCE) this else null

/**
 * Resolves one [BodyPart] into a concrete [GuidanceInstruction.Correct], or null if nothing
 * about it can be confidently turned into a direction (per spec #8 - skipped, not guessed).
 */
private fun resolvePart(
    part: BodyPart,
    reference: PoseSnapshot,
    live: PoseSnapshot,
    comparison: PoseComparisonResult,
): GuidanceInstruction.Correct? = when (part) {
    BodyPart.HEAD -> {
        val angle = comparison.angles[AngleType.HEAD_DIRECTION]
        val delta = angle?.angleDifferenceDegrees
        val ref = angle?.referenceAngleDegrees
        val user = angle?.userAngleDegrees
        if (angle?.status !in setOf(DifferenceStatus.MODERATE, DifferenceStatus.MAJOR) ||
            delta == null || ref == null || user == null
        ) {
            null
        } else {
            GuidanceInstruction.Correct(GuidanceLimb.HEAD, rotationOfDelta(normalizeAngleDelta(ref - user)))
        }
    }
    BodyPart.TORSO -> {
        val angle = comparison.angles[AngleType.TORSO_LEAN]
        val ref = angle?.referenceAngleDegrees
        val user = angle?.userAngleDegrees
        if (angle?.status !in setOf(DifferenceStatus.MODERATE, DifferenceStatus.MAJOR) ||
            ref == null || user == null
        ) {
            null
        } else {
            GuidanceInstruction.Correct(GuidanceLimb.TORSO, rotationOfDelta(normalizeAngleDelta(ref - user)))
        }
    }
    BodyPart.LEFT_ARM -> resolveLimbByLargestDelta(
        reference, live,
        PoseKeypointType.LEFT_WRIST to GuidanceLimb.LEFT_HAND,
        PoseKeypointType.LEFT_ELBOW to GuidanceLimb.LEFT_ELBOW,
    )
    BodyPart.RIGHT_ARM -> resolveLimbByLargestDelta(
        reference, live,
        PoseKeypointType.RIGHT_WRIST to GuidanceLimb.RIGHT_HAND,
        PoseKeypointType.RIGHT_ELBOW to GuidanceLimb.RIGHT_ELBOW,
    )
    BodyPart.LEFT_LEG -> resolveLimbByLargestDelta(
        reference, live,
        PoseKeypointType.LEFT_ANKLE to GuidanceLimb.LEFT_LEG,
        PoseKeypointType.LEFT_KNEE to GuidanceLimb.LEFT_LEG,
    )
    BodyPart.RIGHT_LEG -> resolveLimbByLargestDelta(
        reference, live,
        PoseKeypointType.RIGHT_ANKLE to GuidanceLimb.RIGHT_LEG,
        PoseKeypointType.RIGHT_KNEE to GuidanceLimb.RIGHT_LEG,
    )
}

/** Picks whichever of two candidate keypoints (e.g. wrist vs elbow) has the larger, confidently
 *  measurable difference, and turns *that one* into a direction - so the sentence names the part
 *  that actually needs to move most ("hand" vs "elbow"), same as the spec's own two examples. */
private fun resolveLimbByLargestDelta(
    reference: PoseSnapshot,
    live: PoseSnapshot,
    primary: Pair<PoseKeypointType, GuidanceLimb>,
    secondary: Pair<PoseKeypointType, GuidanceLimb>,
): GuidanceInstruction.Correct? {
    val primaryDelta = signedKeypointDelta(primary.first, reference, live)
    val secondaryDelta = signedKeypointDelta(secondary.first, reference, live)
    val primaryMag = primaryDelta?.let { it.first * it.first + it.second * it.second } ?: -1f
    val secondaryMag = secondaryDelta?.let { it.first * it.first + it.second * it.second } ?: -1f
    val (chosenDelta, chosenLimb) = when {
        primaryMag < 0f && secondaryMag < 0f -> return null
        primaryMag >= secondaryMag -> primaryDelta!! to primary.second
        else -> secondaryDelta!! to secondary.second
    }
    val magnitude = kotlin.math.sqrt(chosenDelta.first * chosenDelta.first + chosenDelta.second * chosenDelta.second)
    if (magnitude < MIN_MEANINGFUL_POSITION_DELTA) return null
    return GuidanceInstruction.Correct(chosenLimb, directionOfDelta(chosenDelta.first, chosenDelta.second))
}

/** Wraps a degree delta into (-180, 180], mirroring PoseComparison.kt's own `normalizeSigned`
 *  (kept as a private copy here so this file stays independent of that file's private helpers). */
private fun normalizeAngleDelta(angle: Float): Float {
    var a = angle % 360f
    if (a > 180f) a -= 360f
    if (a <= -180f) a += 360f
    return a
}

// ---------------------------------------------------------------------------------------------
// Stateful engine: picks one target at a time and applies hysteresis (spec #3, #4, #6)
// ---------------------------------------------------------------------------------------------

/**
 * Turns a stream of [PoseComparisonResult] updates into a stream of single, stable
 * [GuidanceInstruction]s.
 *
 * Two things keep this from flickering (spec #6):
 *  - **One target at a time, held until resolved.** Once a body part is chosen, it stays the
 *    active target - even if a different part briefly becomes more severe - until *that part's*
 *    own difference drops back to [DifferenceStatus.MATCH]. This directly implements spec #3/#4
 *    ("finish correcting the current part before moving to the next") and, as a side effect,
 *    stops the instruction from jumping between parts on every noisy frame.
 *  - **A brief, timed "corrected" message.** When the active target resolves, [GuidanceInstruction.Corrected]
 *    is shown for [CORRECTED_DISPLAY_MS] before the engine moves on to the next target, instead of
 *    silently swapping one instruction for another.
 *
 * Not thread-safe; expected to be driven from a single UI thread (e.g. `remember`ed inside the
 * camera Composable and fed every throttled live-pose update), matching how [LivePoseAnalyzer]
 * and [comparePoses] are already used there.
 */
class PoseGuidanceEngine(private val nowMs: () -> Long = { System.currentTimeMillis() }) {

    private var activeTarget: BodyPart? = null
    private var pendingCorrected: GuidanceLimb? = null
    private var pendingCorrectedShownAtMs: Long = 0L

    /** Feeds one new comparison (or null, when there is no reference and/or no live pose right
     *  now) and returns what should be shown to the user this frame. */
    fun update(
        reference: PoseSnapshot?,
        live: PoseSnapshot?,
        comparison: PoseComparisonResult?,
    ): GuidanceInstruction {
        if (reference == null) {
            reset()
            return GuidanceInstruction.None
        }
        if (live == null) {
            reset()
            return GuidanceInstruction.LowConfidence(LowConfidenceReason.NO_PERSON)
        }
        if (comparison == null) {
            reset()
            return GuidanceInstruction.LowConfidence(LowConfidenceReason.NO_PERSON)
        }

        if (live.overallConfidence < MIN_LIVE_CONFIDENCE_FOR_GUIDANCE) {
            reset()
            val headOk = live.keypoint(PoseKeypointType.HEAD)?.confidence?.let { it >= MIN_KEYPOINT_CONFIDENCE } == true
            val leftAnkleOk = live.keypoint(PoseKeypointType.LEFT_ANKLE)?.confidence?.let { it >= MIN_KEYPOINT_CONFIDENCE } == true
            val rightAnkleOk = live.keypoint(PoseKeypointType.RIGHT_ANKLE)?.confidence?.let { it >= MIN_KEYPOINT_CONFIDENCE } == true
            val leftShoulderOk = live.keypoint(PoseKeypointType.LEFT_SHOULDER)?.confidence?.let { it >= MIN_KEYPOINT_CONFIDENCE } == true
            val rightShoulderOk = live.keypoint(PoseKeypointType.RIGHT_SHOULDER)?.confidence?.let { it >= MIN_KEYPOINT_CONFIDENCE } == true
            val torsoVisible = leftShoulderOk || rightShoulderOk
            val extremityMissing = !headOk || (!leftAnkleOk && !rightAnkleOk)
            return GuidanceInstruction.LowConfidence(
                if (torsoVisible && extremityMissing) {
                    LowConfidenceReason.LIKELY_TOO_CLOSE
                } else {
                    LowConfidenceReason.POORLY_FRAMED
                }
            )
        }

        // A pending "corrected" message: keep showing it until its timer runs out, then fall
        // through to picking the next target below.
        pendingCorrected?.let { limb ->
            if (nowMs() - pendingCorrectedShownAtMs < CORRECTED_DISPLAY_MS) {
                return GuidanceInstruction.Corrected(limb)
            }
            pendingCorrected = null
        }

        // Keep the current target until IT resolves, regardless of what else changed.
        val current = activeTarget
        if (current != null) {
            val status = comparison.parts.firstOrNull { it.part == current }?.status
            if (status == DifferenceStatus.MODERATE || status == DifferenceStatus.MAJOR) {
                val instruction = resolvePart(current, reference, live, comparison)
                if (instruction != null) return instruction
                // The part is still officially "different" but no longer has a confident
                // direction to give (e.g. its keypoints just dropped below the confidence floor)
                // - stop pointing at it rather than guessing.
                activeTarget = null
            } else {
                // Resolved (or now unknown/gone) - announce it once, then look for a new target.
                activeTarget = null
                if (status == DifferenceStatus.MATCH) {
                    val limb = primaryLimbFor(current)
                    pendingCorrected = limb
                    pendingCorrectedShownAtMs = nowMs()
                    return GuidanceInstruction.Corrected(limb)
                }
            }
        }

        // Pick the next target: the most different part (that still yields a real direction),
        // in [PART_PRIORITY] order as a tie-break for equally-severe parts.
        val ranked = PART_PRIORITY
            .mapNotNull { part -> comparison.parts.firstOrNull { it.part == part } }
            .filter { it.status == DifferenceStatus.MODERATE || it.status == DifferenceStatus.MAJOR }
            .sortedByDescending { it.differenceScore ?: 0f }

        for (candidate in ranked) {
            val instruction = resolvePart(candidate.part, reference, live, comparison)
            if (instruction != null) {
                activeTarget = candidate.part
                return instruction
            }
        }

        return if (comparison.overallStatus == DifferenceStatus.MATCH) {
            GuidanceInstruction.PoseReady
        } else {
            // Real differences exist somewhere, but none could be turned into a confident,
            // specific direction (spec #8) - stay quiet rather than show a vague/guessed line.
            GuidanceInstruction.None
        }
    }

    private fun reset() {
        activeTarget = null
        pendingCorrected = null
    }

    private fun primaryLimbFor(part: BodyPart): GuidanceLimb = when (part) {
        BodyPart.HEAD -> GuidanceLimb.HEAD
        BodyPart.TORSO -> GuidanceLimb.TORSO
        BodyPart.LEFT_ARM -> GuidanceLimb.LEFT_HAND
        BodyPart.RIGHT_ARM -> GuidanceLimb.RIGHT_HAND
        BodyPart.LEFT_LEG -> GuidanceLimb.LEFT_LEG
        BodyPart.RIGHT_LEG -> GuidanceLimb.RIGHT_LEG
    }
}
