package com.chikchik.posecamera.util

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gallery album (Pictures/ChikChik) that all saved photos go into. */
const val ALBUM_NAME = "ChikChik"

private const val CAPTURE_DIR = "captures"
private const val EXPLORE_REFERENCE_DIR = "explore_reference"
private const val MIME_JPEG = "image/jpeg"

/** Private temp folder for a photo that was just taken but not saved yet. */
fun captureDir(context: Context): File = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }

fun newCaptureFile(context: Context): File =
    File(captureDir(context), "capture_${System.currentTimeMillis()}.jpg")

/** Removes leftover temp captures (except [keep], e.g. a photo restored after recreation). */
suspend fun clearCaptureCache(context: Context, keep: File? = null) {
    withContext(Dispatchers.IO) {
        captureDir(context).listFiles()?.forEach { file ->
            if (keep == null || file.absolutePath != keep.absolutePath) file.delete()
        }
    }
}

/**
 * Stage 20: private temp folder an Explore photo is downloaded into so it can become the
 * Reference Image (see [com.chikchik.posecamera.util.downloadExploreReferenceImage]). Only
 * ever holds the single most recently picked Explore reference - nothing here is a permanent
 * download, it is just how the existing Uri-based reference pipeline gets its bytes.
 */
fun exploreReferenceDir(context: Context): File =
    File(context.cacheDir, EXPLORE_REFERENCE_DIR).apply { mkdirs() }

fun newExploreReferenceFile(context: Context): File =
    File(exploreReferenceDir(context), "explore_ref_${System.currentTimeMillis()}.jpg")

/** Drops any previously downloaded Explore reference file(s) before/after fetching a new one. */
fun clearExploreReferenceCache(context: Context) {
    exploreReferenceDir(context).listFiles()?.forEach { it.delete() }
}

/**
 * Copies the captured JPEG byte-for-byte into the public gallery (MediaStore),
 * album "ChikChik". No decoding / re-encoding happens, so the original camera
 * quality, resolution and EXIF data are preserved.
 *
 * Returns the MediaStore Uri, or null on failure.
 */
suspend fun saveJpegToGallery(context: Context, source: File): Uri? =
    withContext(Dispatchers.IO) {
        try {
            // The temp capture can vanish (system cleared the cache) or be empty.
            if (!source.exists() || source.length() <= 0L) return@withContext null

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val displayName = "${ALBUM_NAME}_$stamp.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScoped(context, source, displayName)
            } else {
                saveLegacy(context, source, displayName)
            }
        } catch (e: Exception) {
            null
        }
    }

/** Android 10+ : MediaStore + RELATIVE_PATH, no storage permission needed. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun saveScoped(context: Context, source: File, displayName: String): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values) ?: return null
    return try {
        val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot open output stream")
        output.use { out -> source.inputStream().use { input -> input.copyTo(out) } }

        // Publish: the photo becomes visible in Gallery / Google Photos.
        val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        resolver.update(uri, published, null, null)
        uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}

/** Android 9 and below (minSdk 24): write into Pictures/ChikChik, then register in MediaStore. */
@Suppress("DEPRECATION")
private fun saveLegacy(context: Context, source: File, displayName: String): Uri? {
    val albumDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        ALBUM_NAME
    )
    if (!albumDir.exists() && !albumDir.mkdirs()) return null

    var target = File(albumDir, displayName)
    var counter = 1
    while (target.exists()) {
        target = File(albumDir, displayName.removeSuffix(".jpg") + "_$counter.jpg")
        counter++
    }
    try {
        source.copyTo(target)
    } catch (e: Exception) {
        // e.g. disk full: do not leave a broken half-written file in the gallery folder.
        target.delete()
        return null
    }

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, target.name)
        put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        put(MediaStore.Images.Media.SIZE, target.length())
        put(MediaStore.Images.Media.DATA, target.absolutePath)
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) target.delete()
    return uri
}

/**
 * Opens the system share sheet for a temp capture (shared through FileProvider).
 * Returns false (instead of crashing) if the file is gone or nothing can handle it.
 */
fun shareJpeg(context: Context, file: File, chooserTitle: String): Boolean {
    if (!file.exists()) return false
    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_JPEG
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
        true
    } catch (e: Exception) {
        false
    }
}
