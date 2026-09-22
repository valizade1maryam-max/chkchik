package com.chikchik.posecamera.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Longest side (px) of the reference overlay bitmap. Plenty for a phone screen, ~3x lighter than 2048. */
const val OVERLAY_MAX_SIDE = 1600

/** Longest side (px) of the bitmap shown on the "captured photo" screen (display only). */
const val PREVIEW_MAX_SIDE = 2048

/** Never retry a decode with a sample size above this (avoids absurdly tiny results). */
private const val MAX_SAMPLE_SIZE = 64

/**
 * Decodes an image [Uri] (e.g. from the Photo Picker) into a [Bitmap].
 *
 * - Reads the image size first and down-samples so the longest side is at most
 *   [maxSide] px (a 100 MP photo never gets fully decoded into memory).
 * - If the device still runs out of memory, retries with a smaller size.
 * - Applies the EXIF orientation so portrait photos are not shown sideways.
 *   A broken/missing EXIF block never makes the whole load fail.
 *
 * Returns null if the image is corrupt, unsupported, unreadable or too large.
 * Never throws.
 */
suspend fun loadBitmap(context: Context, uri: Uri, maxSide: Int = PREVIEW_MAX_SIDE): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            // 1) Read only the image size (no pixels).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            // 2) Pick a power-of-two sample size.
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2

            // 3) Decode; on OutOfMemoryError try again with half the resolution.
            var decoded: Bitmap? = null
            while (decoded == null && sample <= MAX_SAMPLE_SIZE) {
                try {
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    decoded = resolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                    // null without an exception = corrupt / unsupported file.
                    if (decoded == null) return@withContext null
                } catch (e: OutOfMemoryError) {
                    sample *= 2
                }
            }
            val bitmap = decoded ?: return@withContext null

            // 4) Apply EXIF orientation.
            applyExifOrientation(bitmap, readExifOrientation(resolver, uri))
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

private fun readExifOrientation(resolver: ContentResolver, uri: Uri): Int =
    try {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        // Not a JPEG / broken EXIF: show the image un-rotated instead of failing.
        ExifInterface.ORIENTATION_NORMAL
    }

private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f); matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f); matrix.postScale(-1f, 1f)
        }
        else -> return source
    }
    return try {
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) source.recycle()
        rotated
    } catch (e: OutOfMemoryError) {
        // Not enough memory for the rotated copy: better an un-rotated image than none.
        source
    }
}
