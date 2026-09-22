package com.chikchik.posecamera.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Alpha of the grid lines - thin and translucent so it stays out of the way of composing the shot. */
private const val GRID_LINE_ALPHA = 0.55f

/**
 * Stage 25: simple standard 3x3 (rule-of-thirds) composition grid drawn ABOVE the live camera
 * preview, purely as a Compose layer - not part of the CameraX pipeline, so it can never end up
 * in a saved photo (same "UI overlay vs. captured image" separation the Reference-image overlay
 * and pose overlay already rely on).
 *
 * Deliberately minimal per the Stage 25 spec: two evenly-spaced vertical lines and two evenly-
 * spaced horizontal lines, one fixed thin white/translucent style, no other grid types, no
 * color/thickness options. Purely decorative - no pointer input at all - so it never intercepts
 * taps meant for tap-to-focus, the reference image, or any button drawn above it, and it never
 * touches [com.chikchik.posecamera.ui.overlay.OverlayState] or the pose overlay in any way.
 *
 * Performance: a single [Canvas] draw of 4 straight lines, re-drawn only when the composable's
 * size changes (e.g. screen rotation) - negligible next to the camera preview itself, so it adds
 * no measurable overhead to the live preview.
 */
@Composable
fun CameraGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineColor = Color.White.copy(alpha = GRID_LINE_ALPHA)
        val strokeWidth = 1.dp.toPx()
        val thirdWidth = size.width / 3f
        val thirdHeight = size.height / 3f

        for (i in 1..2) {
            val x = thirdWidth * i
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth,
            )
        }

        for (i in 1..2) {
            val y = thirdHeight * i
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth,
            )
        }
    }
}
