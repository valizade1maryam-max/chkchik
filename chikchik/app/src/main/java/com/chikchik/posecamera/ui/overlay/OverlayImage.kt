package com.chikchik.posecamera.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged

/**
 * Semi-transparent reference image drawn ABOVE the camera preview.
 *
 * It is an ordinary Compose layer, completely separate from CameraX, so it
 * is not part of the camera image.
 *
 * Gestures (when not locked): one finger = move, pinch = zoom,
 * two-finger twist = rotate.
 *
 * Performance: the transform is applied inside [graphicsLayer] { } (a "deferred read"), so
 * dragging/pinching only re-draws one GPU layer and never recomposes anything.
 *
 * Because this layer covers the whole camera preview, it also reports plain
 * taps through [onTap] (used for tap-to-focus). A tap never moves the image.
 */
@Composable
fun OverlayImage(
    image: ImageBitmap,
    state: OverlayState,
    modifier: Modifier = Modifier,
    onTap: ((Offset) -> Unit)? = null,
) {
    val currentOnTap by rememberUpdatedState(onTap)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { newSize ->
                if (newSize.width <= 0 || newSize.height <= 0) return@onSizeChanged
                // When the container size changes (screen rotation, in place or after the
                // Activity was re-created), rescale the pixel offset proportionally.
                val prevW = state.containerWidth
                val prevH = state.containerHeight
                if (prevW > 0 && prevH > 0 && (prevW != newSize.width || prevH != newSize.height)) {
                    state.offsetX *= newSize.width.toFloat() / prevW.toFloat()
                    state.offsetY *= newSize.height.toFloat() / prevH.toFloat()
                }
                state.containerWidth = newSize.width
                state.containerHeight = newSize.height
            }
            // Declared BEFORE the transform detector on purpose: a drag/pinch is consumed
            // by the detector below first, so only real taps reach onTap.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { offset -> currentOnTap?.invoke(offset) })
            }
            .pointerInput(state.locked) {
                if (!state.locked) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        state.applyGesture(
                            centroid = centroid,
                            pan = pan,
                            zoom = zoom,
                            rotationDelta = rotation,
                            containerCenter = Offset(size.width / 2f, size.height / 2f),
                        )
                    }
                }
            }
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = state.offsetX
                    translationY = state.offsetY
                    scaleX = state.scale
                    scaleY = state.scale
                    rotationZ = state.rotation
                    alpha = state.opacity
                    // One image only: fade it directly instead of rendering it into an
                    // off-screen buffer first (cheaper on every frame of a drag).
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
        )
    }
}
