package com.chikchik.posecamera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.chikchik.posecamera.pose.CORE_POSE_CONNECTIONS
import com.chikchik.posecamera.pose.BodyPartVisualStatus
import com.chikchik.posecamera.pose.GuidanceDirection
import com.chikchik.posecamera.pose.PoseKeypointType
import com.chikchik.posecamera.pose.PoseSnapshot
import com.chikchik.posecamera.pose.PoseVisualState
import com.chikchik.posecamera.pose.guidanceAnchorKeypoint
import kotlin.math.cos
import kotlin.math.sin

/** Points below this confidence are not drawn - keeps the Stage 11 preview readable. */
private const val MIN_DRAW_CONFIDENCE = 0.3f

/** Stage 11's original, single-color skeleton - still used verbatim whenever there is no Stage
 *  14 comparison data to color it with (no reference photo yet, or no live pose to compare). */
private val JOINT_COLOR = Color(0xFF00E5A0)
private val JOINT_OUTLINE_COLOR = Color.White

// Stage 14: per-status skeleton colors. NEEDS_CORRECTION reuses the same warm red the rest of
// the app already uses for an "attention" state (the shutter's countdown-cancel color), so this
// doesn't introduce a brand-new color language of its own.
private val STATUS_OK_COLOR = Color(0xFF00E5A0)
private val STATUS_NEEDS_CORRECTION_COLOR = Color(0xFFFF5A4F)
private val STATUS_UNKNOWN_COLOR = Color(0xFFB0B0B0)

/**
 * Stage 11: simple, temporary visualization of the live pose - joint dots and the connecting
 * lines from [CORE_POSE_CONNECTIONS] drawn over the camera preview, so a detected keypoint is
 * actually visible on screen. Nothing here compares poses, judges correctness, or gives any
 * guidance - that is for a later stage; this only shows what was detected.
 *
 * Stage 14: when [visualState] is supplied (there is a reference pose and a live pose to compare
 * it against), each joint/bone is colored by that body part's status instead of the single flat
 * [JOINT_COLOR], and - when Stage 13 is actively asking the user to move one part - a small
 * direction arrow is drawn near it. [visualState] itself is built in `PoseVisualGuidance.kt` from
 * the exact same Stage 12/13 data the text guidance bar reads, so the skeleton coloring, the
 * arrow, and the text line can never disagree with each other (spec requirement #4). When
 * [visualState] is null, this renders exactly as it did in Stage 11.
 *
 * Draws only whatever keypoints [pose] actually has (a partial/incomplete pose from
 * [com.chikchik.posecamera.pose.LivePoseAnalyzer] is expected and handled the same way as a
 * complete one - it just has fewer points to draw).
 *
 * [imageWidth]/[imageHeight] are the analyzed camera frame's own pixel size (already rotated
 * to match what is on screen). Since [CameraPreview] shows the live feed with `FILL_CENTER`
 * (scaled up and center-cropped to fill the screen), the same scale-and-crop math is redone
 * here so the overlay lines up with the preview underneath instead of drifting from it.
 *
 * [mirrorX] should be true when the front camera is active: its on-screen preview is mirrored
 * for the user, but the analyzed frame's raw coordinates are not, so the X axis must be flipped
 * to keep the overlay aligned with the mirrored preview. The same flag also mirrors which
 * on-screen side a LEFT/RIGHT direction arrow points to - see [screenDirection] below.
 */
@Composable
fun PoseOverlay(
    pose: PoseSnapshot,
    imageWidth: Int,
    imageHeight: Int,
    mirrorX: Boolean,
    visualState: PoseVisualState? = null,
    modifier: Modifier = Modifier,
) {
    if (imageWidth <= 0 || imageHeight <= 0) return

    Canvas(modifier = modifier) {
        // Reproduce PreviewView's FILL_CENTER: scale up to cover the box, then center-crop.
        val scale = maxOf(size.width / imageWidth, size.height / imageHeight)
        val drawnWidth = imageWidth * scale
        val drawnHeight = imageHeight * scale
        val offsetX = (size.width - drawnWidth) / 2f
        val offsetY = (size.height - drawnHeight) / 2f

        fun toScreen(xNorm: Float, yNorm: Float): Offset {
            val x = if (mirrorX) 1f - xNorm else xNorm
            return Offset(offsetX + x * drawnWidth, offsetY + yNorm * drawnHeight)
        }

        fun colorFor(type: PoseKeypointType): Color {
            val status = visualState?.keypointStatus?.get(type) ?: return JOINT_COLOR
            return status.toColor()
        }

        // Bones first so the joint dots are drawn on top of the lines. A bone between two
        // regions (e.g. neck -> shoulder, head vs. shoulders) is colored by whichever endpoint
        // is worse, so a real problem is never hidden by an "ok" neighbor.
        for (connection in CORE_POSE_CONNECTIONS) {
            val from = pose.keypoint(connection.from) ?: continue
            val to = pose.keypoint(connection.to) ?: continue
            if (from.confidence < MIN_DRAW_CONFIDENCE || to.confidence < MIN_DRAW_CONFIDENCE) continue
            val color = if (visualState == null) {
                JOINT_COLOR
            } else {
                worseStatus(
                    visualState.keypointStatus[connection.from],
                    visualState.keypointStatus[connection.to],
                ).toColor()
            }
            drawLine(
                color = color,
                start = toScreen(from.xNorm, from.yNorm),
                end = toScreen(to.xNorm, to.yNorm),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }

        for (type in PoseKeypointType.CORE) {
            val point = pose.keypoint(type) ?: continue
            if (point.confidence < MIN_DRAW_CONFIDENCE) continue
            val center = toScreen(point.xNorm, point.yNorm)
            val color = colorFor(type)
            drawCircle(color = JOINT_OUTLINE_COLOR, radius = 10f, center = center)
            drawCircle(color = color, radius = 10f, center = center, style = Stroke(width = 3f))
        }

        // Stage 14: a single, simple direction arrow for whichever correction Stage 13 is
        // currently showing in the text bar - never more than one at a time, matching the text.
        val correction = visualState?.activeCorrection
        if (correction != null) {
            val anchorType = guidanceAnchorKeypoint(correction.limb)
            val anchorPoint = pose.keypoint(anchorType)
            if (anchorPoint != null && anchorPoint.confidence >= MIN_DRAW_CONFIDENCE) {
                val anchor = toScreen(anchorPoint.xNorm, anchorPoint.yNorm)
                drawGuidanceArrow(anchor, screenDirection(correction.direction, mirrorX), STATUS_NEEDS_CORRECTION_COLOR)
            }
        }
    }
}

private fun BodyPartVisualStatus.toColor(): Color = when (this) {
    BodyPartVisualStatus.OK -> STATUS_OK_COLOR
    BodyPartVisualStatus.NEEDS_CORRECTION -> STATUS_NEEDS_CORRECTION_COLOR
    BodyPartVisualStatus.UNKNOWN -> STATUS_UNKNOWN_COLOR
}

/** The more severe of two (possibly missing) statuses - NEEDS_CORRECTION beats UNKNOWN beats OK
 *  - used to color a bone that connects two different regions. */
private fun worseStatus(a: BodyPartVisualStatus?, b: BodyPartVisualStatus?): BodyPartVisualStatus {
    val severity = { s: BodyPartVisualStatus? ->
        when (s) {
            BodyPartVisualStatus.NEEDS_CORRECTION -> 2
            BodyPartVisualStatus.UNKNOWN -> 1
            BodyPartVisualStatus.OK -> 0
            null -> 0
        }
    }
    return if (severity(a) >= severity(b)) (a ?: BodyPartVisualStatus.OK) else (b ?: BodyPartVisualStatus.OK)
}

/**
 * Converts a [GuidanceDirection] (defined in terms of the *person's own* left/right - see
 * `PoseGuidance.kt`'s file doc comment) into the left/right that should be drawn on THIS screen.
 *
 * A raw (un-mirrored) camera frame always shows the person's own right on the smaller-x (left)
 * side of the frame - the same inversion as a photo of someone facing you.
 *  - Back camera ([mirrorX] false): the screen shows the raw frame as-is, so that inversion
 *    carries straight through - a RIGHT instruction must be drawn pointing to screen-left.
 *  - Front camera ([mirrorX] true): the on-screen preview is itself mirrored relative to raw
 *    space, which cancels the inversion above - a RIGHT instruction is drawn pointing to
 *    screen-right, matching what the user sees in an ordinary mirror.
 * UP/DOWN are never affected, since Y is never mirrored.
 */
private fun screenDirection(direction: GuidanceDirection, mirrorX: Boolean): GuidanceDirection {
    if (mirrorX) return direction
    return when (direction) {
        GuidanceDirection.LEFT -> GuidanceDirection.RIGHT
        GuidanceDirection.RIGHT -> GuidanceDirection.LEFT
        GuidanceDirection.ROTATE_LEFT -> GuidanceDirection.ROTATE_RIGHT
        GuidanceDirection.ROTATE_RIGHT -> GuidanceDirection.ROTATE_LEFT
        else -> direction
    }
}

/** How far from the joint the arrow starts, and how long it is - small and out of the way, per
 *  spec's "simple, non-intrusive" requirement. */
private const val ARROW_GAP_PX = 22f
private const val ARROW_LENGTH_PX = 40f
private const val ARROW_HEAD_PX = 16f
private const val ROTATE_RADIUS_PX = 30f

/** Draws one small, simple guidance arrow near [anchor] - a straight arrow for a translation
 *  (UP/DOWN/LEFT/RIGHT) or a short curved arrow for a rotation. Already-screen-space [direction]
 *  (see [screenDirection]) - this function does no further mirroring of its own. */
private fun DrawScope.drawGuidanceArrow(anchor: Offset, direction: GuidanceDirection, color: Color) {
    when (direction) {
        GuidanceDirection.UP -> drawTranslationArrow(anchor, Offset(0f, -1f), color)
        GuidanceDirection.DOWN -> drawTranslationArrow(anchor, Offset(0f, 1f), color)
        GuidanceDirection.LEFT -> drawTranslationArrow(anchor, Offset(-1f, 0f), color)
        GuidanceDirection.RIGHT -> drawTranslationArrow(anchor, Offset(1f, 0f), color)
        GuidanceDirection.ROTATE_LEFT -> drawRotationArrow(anchor, clockwise = false, color)
        GuidanceDirection.ROTATE_RIGHT -> drawRotationArrow(anchor, clockwise = true, color)
    }
}

private fun DrawScope.drawTranslationArrow(anchor: Offset, dir: Offset, color: Color) {
    val start = Offset(anchor.x + dir.x * ARROW_GAP_PX, anchor.y + dir.y * ARROW_GAP_PX)
    val end = Offset(start.x + dir.x * ARROW_LENGTH_PX, start.y + dir.y * ARROW_LENGTH_PX)
    drawLine(color = color, start = start, end = end, strokeWidth = 7f, cap = StrokeCap.Round)

    // Arrowhead: a small filled chevron at the tip, perpendicular to the direction of travel.
    val perp = Offset(-dir.y, dir.x)
    val headBase = Offset(end.x - dir.x * ARROW_HEAD_PX, end.y - dir.y * ARROW_HEAD_PX)
    val p1 = Offset(headBase.x + perp.x * ARROW_HEAD_PX * 0.6f, headBase.y + perp.y * ARROW_HEAD_PX * 0.6f)
    val p2 = Offset(headBase.x - perp.x * ARROW_HEAD_PX * 0.6f, headBase.y - perp.y * ARROW_HEAD_PX * 0.6f)
    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawRotationArrow(anchor: Offset, clockwise: Boolean, color: Color) {
    val rect = Rect(center = anchor, radius = ROTATE_RADIUS_PX)
    val startAngleDeg = if (clockwise) 200f else -20f
    val sweepDeg = if (clockwise) 140f else -140f
    drawArc(
        color = color,
        startAngle = startAngleDeg,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 6f, cap = StrokeCap.Round),
    )

    // Arrowhead at the end of the arc, tangent to the curve.
    val endAngleRad = Math.toRadians((startAngleDeg + sweepDeg).toDouble())
    val tip = Offset(
        anchor.x + ROTATE_RADIUS_PX * cos(endAngleRad).toFloat(),
        anchor.y + ROTATE_RADIUS_PX * sin(endAngleRad).toFloat(),
    )
    val tangentRad = endAngleRad + (if (clockwise) Math.PI / 2 else -Math.PI / 2)
    val dir = Offset(cos(tangentRad).toFloat(), sin(tangentRad).toFloat())
    val perp = Offset(-dir.y, dir.x)
    val tipOut = Offset(tip.x + dir.x * ARROW_HEAD_PX, tip.y + dir.y * ARROW_HEAD_PX)
    val p1 = Offset(tip.x + perp.x * ARROW_HEAD_PX * 0.6f, tip.y + perp.y * ARROW_HEAD_PX * 0.6f)
    val p2 = Offset(tip.x - perp.x * ARROW_HEAD_PX * 0.6f, tip.y - perp.y * ARROW_HEAD_PX * 0.6f)
    val path = Path().apply {
        moveTo(tipOut.x, tipOut.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color = color)
}
