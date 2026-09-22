package com.chikchik.posecamera.ui.camera

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chikchik.posecamera.R
import com.chikchik.posecamera.pose.GuidanceDirection
import com.chikchik.posecamera.pose.GuidanceInstruction
import com.chikchik.posecamera.pose.GuidanceLimb
import com.chikchik.posecamera.pose.LowConfidenceReason
import com.chikchik.posecamera.pose.PoseComparisonResult
import com.chikchik.posecamera.pose.PoseGuidanceEngine
import com.chikchik.posecamera.pose.PoseSnapshot
import kotlinx.coroutines.delay

/**
 * Stage 13: drives a [PoseGuidanceEngine] off the live Stage 12 comparison and exposes the
 * [GuidanceInstruction] currently being shown.
 *
 * Ticks on a short fixed interval (independent of how often a new camera frame actually arrives)
 * purely so a timed [GuidanceInstruction.Corrected] message can expire even while the user is
 * holding still - [PoseGuidanceEngine] itself remains a plain, frame-driven function; this is
 * just what re-invokes it on a clock.
 */
@Composable
fun rememberPoseGuidanceInstruction(
    referenceSnapshot: PoseSnapshot?,
    livePose: PoseSnapshot?,
    poseComparison: PoseComparisonResult?,
): GuidanceInstruction {
    val engine = remember { PoseGuidanceEngine() }
    var instruction by remember { mutableStateOf<GuidanceInstruction>(GuidanceInstruction.None) }
    val latestReference = rememberUpdatedState(referenceSnapshot)
    val latestLive = rememberUpdatedState(livePose)
    val latestComparison = rememberUpdatedState(poseComparison)

    LaunchedEffect(Unit) {
        while (true) {
            instruction = engine.update(latestReference.value, latestLive.value, latestComparison.value)
            delay(200)
        }
    }
    return instruction
}

/**
 * Bottom-of-camera text bar (spec #10): a single short line of guidance, styled to match the
 * app's existing dark/minimal overlays (semi-transparent black pill, white text) rather than
 * introducing a new visual language. Renders nothing for [GuidanceInstruction.None] so it never
 * shows an empty bar when there is no reference pose yet.
 */
@Composable
fun PoseGuidanceBar(instruction: GuidanceInstruction, modifier: Modifier = Modifier) {
    val text = guidanceText(instruction) ?: return
    val isReady = instruction is GuidanceInstruction.PoseReady
    Crossfade(targetState = text, modifier = modifier, animationSpec = tween(150), label = "guidance") { shown ->
        Text(
            text = shown,
            color = if (isReady) MaterialTheme.colorScheme.primary else Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .widthIn(max = 320.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun guidanceText(instruction: GuidanceInstruction): String? = when (instruction) {
    is GuidanceInstruction.None -> null
    is GuidanceInstruction.PoseReady -> stringResource(R.string.guidance_ready)
    is GuidanceInstruction.LowConfidence -> when (instruction.reason) {
        LowConfidenceReason.NO_PERSON -> stringResource(R.string.guidance_hint_frame)
        LowConfidenceReason.LIKELY_TOO_CLOSE -> stringResource(R.string.guidance_hint_distance)
        LowConfidenceReason.POORLY_FRAMED -> stringResource(R.string.guidance_hint_frame)
    }
    is GuidanceInstruction.Corrected -> stringResource(
        R.string.guidance_corrected_template, plainLimbName(instruction.limb)
    )
    is GuidanceInstruction.Correct -> stringResource(
        R.string.guidance_template, possessiveLimbName(instruction.limb), directionPhrase(instruction.direction)
    )
}

@Composable
private fun possessiveLimbName(limb: GuidanceLimb): String = stringResource(
    when (limb) {
        GuidanceLimb.HEAD -> R.string.guidance_limb_head
        GuidanceLimb.TORSO -> R.string.guidance_limb_torso
        GuidanceLimb.LEFT_HAND -> R.string.guidance_limb_left_hand
        GuidanceLimb.RIGHT_HAND -> R.string.guidance_limb_right_hand
        GuidanceLimb.LEFT_ELBOW -> R.string.guidance_limb_left_elbow
        GuidanceLimb.RIGHT_ELBOW -> R.string.guidance_limb_right_elbow
        GuidanceLimb.LEFT_LEG -> R.string.guidance_limb_left_leg
        GuidanceLimb.RIGHT_LEG -> R.string.guidance_limb_right_leg
    }
)

@Composable
private fun plainLimbName(limb: GuidanceLimb): String = stringResource(
    when (limb) {
        GuidanceLimb.HEAD -> R.string.guidance_limb_plain_head
        GuidanceLimb.TORSO -> R.string.guidance_limb_plain_torso
        GuidanceLimb.LEFT_HAND -> R.string.guidance_limb_plain_left_hand
        GuidanceLimb.RIGHT_HAND -> R.string.guidance_limb_plain_right_hand
        GuidanceLimb.LEFT_ELBOW -> R.string.guidance_limb_plain_left_elbow
        GuidanceLimb.RIGHT_ELBOW -> R.string.guidance_limb_plain_right_elbow
        GuidanceLimb.LEFT_LEG -> R.string.guidance_limb_plain_left_leg
        GuidanceLimb.RIGHT_LEG -> R.string.guidance_limb_plain_right_leg
    }
)

@Composable
private fun directionPhrase(direction: GuidanceDirection): String = stringResource(
    when (direction) {
        GuidanceDirection.UP -> R.string.guidance_dir_up
        GuidanceDirection.DOWN -> R.string.guidance_dir_down
        GuidanceDirection.LEFT -> R.string.guidance_dir_left
        GuidanceDirection.RIGHT -> R.string.guidance_dir_right
        GuidanceDirection.ROTATE_LEFT -> R.string.guidance_dir_rotate_left
        GuidanceDirection.ROTATE_RIGHT -> R.string.guidance_dir_rotate_right
    }
)
