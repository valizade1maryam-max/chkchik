package com.chikchik.posecamera.ui.camera

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.chikchik.posecamera.R
import com.chikchik.posecamera.pose.GuidanceInstruction
import com.chikchik.posecamera.pose.PoseComparisonResult
import com.chikchik.posecamera.pose.PoseVisualGuidanceEngine
import com.chikchik.posecamera.pose.PoseVisualState
import kotlinx.coroutines.delay

/**
 * Stage 14: drives a [PoseVisualGuidanceEngine] off the same Stage 12 comparison and Stage 13
 * instruction the text guidance bar already reads, on the same tick interval as
 * `rememberPoseGuidanceInstruction` (Stage 13), so the visuals never lag a frame behind or ahead
 * of the text they must stay consistent with.
 */
@Composable
fun rememberPoseVisualState(
    poseComparison: PoseComparisonResult?,
    guidanceInstruction: GuidanceInstruction,
): PoseVisualState? {
    val engine = remember { PoseVisualGuidanceEngine() }
    var state by remember { mutableStateOf<PoseVisualState?>(null) }
    val latestComparison = rememberUpdatedState(poseComparison)
    val latestInstruction = rememberUpdatedState(guidanceInstruction)

    LaunchedEffect(Unit) {
        while (true) {
            state = engine.update(latestComparison.value, latestInstruction.value)
            delay(200)
        }
    }
    return state
}

/**
 * Spec #5: a short, general pose-readiness line ("در حال تنظیم ژست..." / "ژست آماده است ✓"),
 * separate from Stage 13's specific per-limb instruction below it. Reads [visualState]'s own
 * already-de-flickered [PoseVisualState.overallReady] - built from the real Stage 12 comparison,
 * never guessed. Renders nothing until there is an actual live comparison to report on, so it
 * never shows a stale/empty status before a reference pose exists.
 */
@Composable
fun PoseStatusBadge(visualState: PoseVisualState?, modifier: Modifier = Modifier) {
    if (visualState == null) return
    val text = stringResource(
        if (visualState.overallReady) R.string.pose_status_ready else R.string.pose_status_adjusting
    )
    Crossfade(targetState = text, modifier = modifier, animationSpec = tween(150), label = "poseStatus") { shown ->
        Text(
            text = shown,
            color = if (visualState.overallReady) MaterialTheme.colorScheme.primary else Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
