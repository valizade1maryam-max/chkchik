package com.chikchik.posecamera.ui.overlay

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

const val DEFAULT_OPACITY = 0.45f
const val MIN_SCALE = 0.2f
const val MAX_SCALE = 8f

/**
 * Holds the user-controlled transform of the reference-image overlay.
 * Offset is measured from the centre of the screen, in pixels.
 */
@Stable
class OverlayState(
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    rotation: Float = 0f,
    opacity: Float = DEFAULT_OPACITY,
    locked: Boolean = false,
    flipped: Boolean = false,
    containerWidth: Int = 0,
    containerHeight: Int = 0,
) {
    var scale by mutableFloatStateOf(scale)
    var offsetX by mutableFloatStateOf(offsetX)
    var offsetY by mutableFloatStateOf(offsetY)
    var rotation by mutableFloatStateOf(rotation) // degrees, clockwise
    var opacity by mutableFloatStateOf(opacity)
    var locked by mutableStateOf(locked)

    /**
     * Stage 24: true when the reference image is mirrored left-right (horizontal flip only -
     * never affects the live camera feed, which is a separate layer entirely - see
     * [com.chikchik.posecamera.ui.overlay.OverlayImage]). Persists for the session like the
     * other transform fields below, and is cleared by [reset] and whenever a new reference
     * image is picked (that path already calls [reset]).
     */
    var flipped by mutableStateOf(flipped)

    /**
     * Size (px) of the screen area the offsets above are expressed in. It is saved together
     * with the transform, so after a rotation (Activity re-created) the offset can be rescaled
     * to the new size instead of drifting off-screen.
     */
    var containerWidth by mutableIntStateOf(containerWidth)
    var containerHeight by mutableIntStateOf(containerHeight)

    /** Back to the default position/size/angle/opacity/flip. Lock state is kept. */
    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotation = 0f
        opacity = DEFAULT_OPACITY
        flipped = false
    }

    /**
     * Applies one multi-touch gesture step so the point under the fingers
     * stays under the fingers (pan + pinch-zoom + two-finger rotate).
     *
     * @param centroid gesture centre in container coordinates
     * @param containerCenter centre of the container (image pivot)
     */
    fun applyGesture(
        centroid: Offset,
        pan: Offset,
        zoom: Float,
        rotationDelta: Float,
        containerCenter: Offset,
    ) {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val effectiveZoom = newScale / scale

        // Current image centre relative to the gesture centroid.
        val dx = containerCenter.x + offsetX - centroid.x
        val dy = containerCenter.y + offsetY - centroid.y

        val rad = Math.toRadians(rotationDelta.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()

        // Rotate + scale that vector around the centroid, then add the pan.
        val rx = effectiveZoom * (dx * c - dy * s)
        val ry = effectiveZoom * (dx * s + dy * c)

        offsetX = centroid.x + rx + pan.x - containerCenter.x
        offsetY = centroid.y + ry + pan.y - containerCenter.y
        scale = newScale
        rotation += rotationDelta
    }

    companion object {
        val Saver = listSaver<OverlayState, Any>(
            save = {
                listOf(
                    it.scale, it.offsetX, it.offsetY, it.rotation, it.opacity, it.locked,
                    it.containerWidth, it.containerHeight, it.flipped,
                )
            },
            restore = {
                OverlayState(
                    scale = it[0] as Float,
                    offsetX = it[1] as Float,
                    offsetY = it[2] as Float,
                    rotation = it[3] as Float,
                    opacity = it[4] as Float,
                    locked = it[5] as Boolean,
                    containerWidth = it[6] as Int,
                    containerHeight = it[7] as Int,
                    flipped = it.getOrNull(8) as? Boolean ?: false,
                )
            }
        )
    }
}
