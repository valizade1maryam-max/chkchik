package com.chikchik.posecamera.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val CONNECT_TIMEOUT_MS = 12_000
private const val READ_TIMEOUT_MS = 12_000

/**
 * Small in-memory-only cache for decoded Explore thumbnails (not persisted to disk - stage 16
 * asks for no complex cache system, and there was no existing image cache in the project to
 * reuse). Bounded by entry count so scrolling a long feed can't grow it without limit.
 */
private const val MAX_CACHED_BITMAPS = 80
private val remoteBitmapCache = LruCache<String, Bitmap>(MAX_CACHED_BITMAPS)

/**
 * Downloads and decodes a network image.
 *
 * Used for Explore thumbnails, which the source API already resizes server-side (Pexels'
 * "medium" size), so - unlike [loadBitmap] for full-size local/reference photos - no extra
 * downsampling pass is needed here.
 *
 * Runs on [Dispatchers.IO]. Never throws: returns null on any network or decode failure so a
 * single broken thumbnail can't crash the Explore grid.
 */
suspend fun loadRemoteBitmap(url: String): Bitmap? {
    remoteBitmapCache.get(url)?.let { return it }

    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val bytes = connection.inputStream.use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            remoteBitmapCache.put(url, bitmap)
            bitmap
        } catch (e: IOException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

/**
 * Stage 20: downloads the Explore photo at [url] into the app's private cache and returns it
 * as a content:// Uri (via the app's existing [FileProvider], already used for sharing
 * captures - see PhotoStorage.kt).
 *
 * This is the only bridge between Explore and the reference system: it does not decode a
 * Bitmap, apply any resizing or run pose detection itself - it just gets the bytes onto disk
 * behind a Uri. Everything after that (down-sampling to [OVERLAY_MAX_SIDE], EXIF handling,
 * pose extraction) is the same [loadBitmap] pipeline already used for a gallery-picked image,
 * so "Use as Reference" cannot drift from how a normal reference photo is prepared.
 *
 * Never throws: returns null on any network, storage or decoding problem so a failed/offline
 * download shows a message instead of crashing.
 */
suspend fun downloadExploreReferenceImage(context: Context, url: String): Uri? =
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            // Only one Explore-sourced reference is ever needed at a time - this is a cache
            // for the pipeline's Uri requirement, not a permanent download/gallery of images.
            clearExploreReferenceCache(context)
            val target = newExploreReferenceFile(context)
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (!target.exists() || target.length() <= 0L) {
                target.delete()
                return@withContext null
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            connection?.disconnect()
        }
    }
