package com.chikchik.posecamera.pose

/**
 * Stage 14: turns the *same* data Stage 12 ([PoseComparisonResult]) and Stage 13
 * ([GuidanceInstruction]) already computed into a form the UI can draw on top of the camera
 * preview - per-body-part status for the skeleton, and which single correction (if any) should
 * get a direction arrow.
 *
 * This file deliberately does **not** re-run any comparison or re-decide which body part needs
 * correcting - see the spec's requirement #4 ("there must not be two separate systems deciding
 * what needs to move"). Every value here is read directly off [PoseComparisonResult] (Stage 12)
 * or the [GuidanceInstruction] Stage 13 already picked for the text bar, so the skeleton
 * coloring, the arrow, and the text line can never disagree with each other.
 *
 * Like [PoseComparison.kt][comparePoses] and [PoseGuidance.kt][PoseGuidanceEngine], this file is
 * pure Kotlin - no Compose, no Android `Context`. Drawing itself happens in
 * `ui/camera/PoseOverlay.kt`.
 */

// ---------------------------------------------------------------------------------------------
// Output data model
// ---------------------------------------------------------------------------------------------

/**
 * The seven body regions the Stage 14 spec asks for a status on. Deliberately a bit finer than
 * [BodyPart]: it splits [BodyPart.TORSO]'s own keypoints (hips) from the shoulders, since the
 * spec calls out "shoulders" as its own region distinct from "upper body".
 */
enum class VisualBodyRegion { HEAD, LEFT_HAND, RIGHT_HAND, SHOULDERS, TORSO, LEFT_LEG, RIGHT_LEG }

/** Simplified, UI-facing version of [DifferenceStatus] - MATCH/MINOR collapse into "OK" (spec
 *  only asks for three buckets: correct, needs correction, or unknown). */
enum class BodyPartVisualStatus { OK, NEEDS_CORRECTION, UNKNOWN }

/**
 * Everything the camera screen needs to draw Stage 14's visual guidance for one frame.
 *
 * @param regionStatus current (already de-flickered) status of each of the 7 regions.
 * @param keypointStatus the same information expanded to every core keypoint, so the skeleton
 *   overlay can color each joint/bone without repeating the region -> keypoint mapping itself.
 * @param activeCorrection the exact [GuidanceInstruction.Correct] currently shown in Stage 13's
 *   text bar (or null when nothing is being actively corrected right now) - the arrow is drawn
 *   from this and only this, so it is never out of sync with the text.
 * @param overallReady de-flickered "pose is within the acceptable range" flag, for the general
 *   status line (spec #5).
 */
data class PoseVisualState(
    val regionStatus: Map<VisualBodyRegion, BodyPartVisualStatus>,
    val keypointStatus: Map<PoseKeypointType, BodyPartVisualStatus>,
    val activeCorrection: GuidanceInstruction.Correct?,
    val overallReady: Boolean,
)

// ---------------------------------------------------------------------------------------------
// Region <-> existing Stage 12/13 data mapping
// ---------------------------------------------------------------------------------------------

/** The 5 regions that map onto an existing [BodyPart] one-to-one (everything except
 *  [VisualBodyRegion.SHOULDERS], which cuts across [BodyPart.TORSO]'s own keypoints). */
private val REGION_TO_BODY_PART: Map<VisualBodyRegion, BodyPart> = mapOf(
    VisualBodyRegion.HEAD to BodyPart.HEAD,
    VisualBodyRegion.LEFT_HAND to BodyPart.LEFT_ARM,
    VisualBodyRegion.RIGHT_HAND to BodyPart.RIGHT_ARM,
    VisualBodyRegion.TORSO to BodyPart.TORSO,
    VisualBodyRegion.LEFT_LEG to BodyPart.LEFT_LEG,
    VisualBodyRegion.RIGHT_LEG to BodyPart.RIGHT_LEG,
)

/** Which core keypoints belong to each region, for coloring the skeleton overlay. Every core
 *  keypoint appears in exactly one region. */
internal val REGION_KEYPOINTS: Map<VisualBodyRegion, List<PoseKeypointType>> = mapOf(
    VisualBodyRegion.HEAD to listOf(PoseKeypointType.HEAD, PoseKeypointType.NECK),
    VisualBodyRegion.LEFT_HAND to listOf(PoseKeypointType.LEFT_ELBOW, PoseKeypointType.LEFT_WRIST),
    VisualBodyRegion.RIGHT_HAND to listOf(PoseKeypointType.RIGHT_ELBOW, PoseKeypointType.RIGHT_WRIST),
    VisualBodyRegion.SHOULDERS to listOf(PoseKeypointType.LEFT_SHOULDER, PoseKeypointType.RIGHT_SHOULDER),
    VisualBodyRegion.TORSO to listOf(PoseKeypointType.LEFT_HIP, PoseKeypointType.RIGHT_HIP),
    VisualBodyRegion.LEFT_LEG to listOf(PoseKeypointType.LEFT_KNEE, PoseKeypointType.LEFT_ANKLE),
    VisualBodyRegion.RIGHT_LEG to listOf(PoseKeypointType.RIGHT_KNEE, PoseKeypointType.RIGHT_ANKLE),
)

/** Which live keypoint an arrow for a given [GuidanceLimb] should be anchored near - the same
 *  limb naming Stage 13 already uses for its sentence ("hand" -> wrist, "leg" -> ankle, ...). */
internal fun guidanceAnchorKeypoint(limb: GuidanceLimb): PoseKeypointType = when (limb) {
    GuidanceLimb.HEAD -> PoseKeypointType.HEAD
    GuidanceLimb.TORSO -> PoseKeypointType.NECK
    GuidanceLimb.LEFT_HAND -> PoseKeypointType.LEFT_WRIST
    GuidanceLimb.RIGHT_HAND -> PoseKeypointType.RIGHT_WRIST
    GuidanceLimb.LEFT_ELBOW -> PoseKeypointType.LEFT_ELBOW
    GuidanceLimb.RIGHT_ELBOW -> PoseKeypointType.RIGHT_ELBOW
    GuidanceLimb.LEFT_LEG -> PoseKeypointType.LEFT_ANKLE
    GuidanceLimb.RIGHT_LEG -> PoseKeypointType.RIGHT_ANKLE
}

private fun statusFromDifference(status: DifferenceStatus): BodyPartVisualStatus = when (status) {
    DifferenceStatus.MATCH, DifferenceStatus.MINOR -> BodyPartVisualStatus.OK
    DifferenceStatus.MODERATE, DifferenceStatus.MAJOR -> BodyPartVisualStatus.NEEDS_CORRECTION
    DifferenceStatus.UNKNOWN -> BodyPartVisualStatus.UNKNOWN
}

/** Combines two [DifferenceStatus]es (e.g. left/right shoulder) into one, per spec's own "worse
 *  wins" idea - but a lone UNKNOWN never hides a real reading from the other side. */
private fun combineDifference(a: DifferenceStatus, b: DifferenceStatus): DifferenceStatus {
    if (a == DifferenceStatus.UNKNOWN) return b
    if (b == DifferenceStatus.UNKNOWN) return a
    val severity = listOf(
        DifferenceStatus.MATCH, DifferenceStatus.MINOR, DifferenceStatus.MODERATE, DifferenceStatus.MAJOR
    )
    return if (severity.indexOf(a) >= severity.indexOf(b)) a else b
}

/** Reads this frame's raw (not yet de-flickered) status straight off Stage 12's own
 *  [PoseComparisonResult] - never a new measurement. */
private fun rawRegionStatuses(comparison: PoseComparisonResult): Map<VisualBodyRegion, DifferenceStatus> =
    VisualBodyRegion.entries.associateWith { region ->
        if (region == VisualBodyRegion.SHOULDERS) {
            val left = comparison.keypoints[PoseKeypointType.LEFT_SHOULDER]?.status ?: DifferenceStatus.UNKNOWN
            val right = comparison.keypoints[PoseKeypointType.RIGHT_SHOULDER]?.status ?: DifferenceStatus.UNKNOWN
            combineDifference(left, right)
        } else {
            val part = REGION_TO_BODY_PART.getValue(region)
            comparison.parts.first { it.part == part }.status
        }
    }

// ---------------------------------------------------------------------------------------------
// Stateful engine: de-flickers the per-region statuses and the overall-ready flag (spec #6)
// ---------------------------------------------------------------------------------------------

/** A status must be the raw reading for this long, continuously, before the displayed value
 *  actually changes - mirrors the hysteresis idea [PoseGuidanceEngine] already uses for text
 *  (its "hold the active target" rule / [CORRECTED_DISPLAY_MS]), applied here to the visuals. */
private const val VISUAL_STATUS_HOLD_MS = 500L

/**
 * Turns a stream of [PoseComparisonResult] + [GuidanceInstruction] updates into a stream of
 * stable [PoseVisualState]s, so the skeleton colors and the overall-ready badge don't flip back
 * and forth on every small change in the underlying keypoints (spec #6).
 *
 * Not thread-safe; meant to be driven the same way [PoseGuidanceEngine] is - `remember`ed inside
 * the camera Composable and fed on the same clock as the text guidance.
 */
class PoseVisualGuidanceEngine(private val nowMs: () -> Long = { System.currentTimeMillis() }) {

    /** Generic "don't accept a new value until it's been the raw reading continuously for
     *  [VISUAL_STATUS_HOLD_MS]" holder, shared by the per-region statuses and the ready flag. */
    private class Hysteresis<T>(initial: T) {
        var displayed: T = initial
            private set
        private var pending: T = initial
        private var pendingSinceMs: Long = 0L

        fun update(raw: T, now: Long): T {
            if (raw != pending) {
                pending = raw
                pendingSinceMs = now
            }
            if (pending != displayed && now - pendingSinceMs >= VISUAL_STATUS_HOLD_MS) {
                displayed = pending
            }
            return displayed
        }
    }

    private val regionHysteresis = mutableMapOf<VisualBodyRegion, Hysteresis<BodyPartVisualStatus>>()
    private var readyHysteresis: Hysteresis<Boolean>? = null

    /** Feeds one new comparison/instruction pair and returns the stable visual state to draw
     *  this frame - null exactly when [comparePoses] itself has nothing to compare (spec: no
     *  reference and/or no live pose), so the caller can fall back to Stage 11's plain skeleton. */
    fun update(comparison: PoseComparisonResult?, instruction: GuidanceInstruction): PoseVisualState? {
        if (comparison == null) {
            // Nothing to compare right now - reset so stale hysteresis state doesn't leak into
            // the next reference photo / live pose.
            regionHysteresis.clear()
            readyHysteresis = null
            return null
        }

        val now = nowMs()
        val raw = rawRegionStatuses(comparison)

        val displayedRegions = VisualBodyRegion.entries.associateWith { region ->
            val hysteresis = regionHysteresis.getOrPut(region) {
                Hysteresis(statusFromDifference(raw.getValue(region)))
            }
            hysteresis.update(statusFromDifference(raw.getValue(region)), now)
        }

        val keypointStatus = mutableMapOf<PoseKeypointType, BodyPartVisualStatus>()
        for ((region, types) in REGION_KEYPOINTS) {
            val status = displayedRegions.getValue(region)
            for (type in types) keypointStatus[type] = status
        }

        val readyRaw = comparison.overallStatus == DifferenceStatus.MATCH
        val readyH = readyHysteresis ?: Hysteresis(readyRaw).also { readyHysteresis = it }
        val ready = readyH.update(readyRaw, now)

        return PoseVisualState(
            regionStatus = displayedRegions,
            keypointStatus = keypointStatus,
            // Read straight from Stage 13's own instruction - never re-derived here, so the
            // arrow can never point somewhere the text guidance disagrees with.
            activeCorrection = instruction as? GuidanceInstruction.Correct,
            overallReady = ready,
        )
    }
}
