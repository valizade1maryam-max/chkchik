package com.chikchik.posecamera.explore

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stage 19: local-only persistence for the Explore screen's Favorites and History.
 *
 * Storage choice: [SharedPreferences] holding a small JSON blob per list (via `org.json`,
 * already bundled with Android and already used by [ExploreRepository] - no new dependency
 * added). The project has no database/DataStore dependency yet and both lists are small
 * (History is capped, see [MAX_HISTORY_ENTRIES]), so a key/value store is a better fit for
 * this architecture than pulling in Room or DataStore for two short lists.
 *
 * What is saved is exactly what is needed to show a photo again and re-open its source -
 * the same fields already on [ExplorePhoto] (id, thumbnail URL, dimensions, alt text,
 * attribution, source name/page) - plus a timestamp for History. The actual image bytes are
 * never written to disk; thumbnails are re-downloaded through the existing
 * [com.chikchik.posecamera.util.loadRemoteBitmap] (which already has its own in-memory
 * cache), exactly like the rest of the Explore feed already does.
 *
 * No account, server or sync of any kind - everything here is per-device only.
 *
 * State is exposed as [StateFlow]s so the Compose UI recomposes immediately after a toggle,
 * while the actual disk write happens on [Dispatchers.IO] so it never blocks the UI thread
 * (the same pattern [ExploreRepository] already uses for its network calls).
 */
object ExploreLocalStore {

    private const val PREFS_NAME = "explore_library"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_HISTORY = "history"
    private const val FIELD_VIEWED_AT = "viewedAt"

    /** Keeps History from growing without bound over a long-lived install. */
    private const val MAX_HISTORY_ENTRIES = 200

    private val _favorites = MutableStateFlow<Map<String, ExplorePhoto>>(emptyMap())
    /** Favorited photos, keyed by [ExplorePhoto.id]. Insertion order = favorited order. */
    val favorites: StateFlow<Map<String, ExplorePhoto>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    /** Most-recently-viewed first; at most one entry per photo id. */
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    @Volatile
    private var loaded = false

    /** Loads both lists from disk once per process. Safe to call repeatedly/concurrently. */
    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        withContext(Dispatchers.IO) {
            synchronized(this@ExploreLocalStore) {
                if (loaded) return@withContext
                val prefs = prefs(context)
                _favorites.value = decodeFavorites(prefs.getString(KEY_FAVORITES, null))
                _history.value = decodeHistory(prefs.getString(KEY_HISTORY, null))
                loaded = true
            }
        }
    }

    fun isFavorite(id: String): Boolean = _favorites.value.containsKey(id)

    /** Adds [photo] to Favorites if it isn't favorited yet, otherwise removes it. */
    suspend fun toggleFavorite(context: Context, photo: ExplorePhoto) {
        ensureLoaded(context)
        val updated = if (_favorites.value.containsKey(photo.id)) {
            _favorites.value - photo.id
        } else {
            _favorites.value + (photo.id to photo)
        }
        _favorites.value = updated
        withContext(Dispatchers.IO) {
            prefs(context).edit().putString(KEY_FAVORITES, encodeFavorites(updated)).apply()
        }
    }

    /**
     * Records that [photo] was opened for detail viewing. If it is already the most recent
     * History entry this is a no-op (avoids rewriting storage for repeated opens in a row);
     * otherwise any existing entry for the same photo is dropped and a fresh one is added at
     * the front, so History never lists the same photo twice.
     */
    suspend fun recordViewed(context: Context, photo: ExplorePhoto) {
        ensureLoaded(context)
        if (_history.value.firstOrNull()?.photo?.id == photo.id) return

        val updated = (listOf(HistoryEntry(photo, System.currentTimeMillis())) +
            _history.value.filterNot { it.photo.id == photo.id })
            .take(MAX_HISTORY_ENTRIES)
        _history.value = updated
        withContext(Dispatchers.IO) {
            prefs(context).edit().putString(KEY_HISTORY, encodeHistory(updated)).apply()
        }
    }

    suspend fun clearHistory(context: Context) {
        ensureLoaded(context)
        _history.value = emptyList()
        withContext(Dispatchers.IO) {
            prefs(context).edit().remove(KEY_HISTORY).apply()
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- JSON encode/decode -------------------------------------------------------------

    private fun encodeFavorites(map: Map<String, ExplorePhoto>): String {
        val array = JSONArray()
        map.values.forEach { array.put(photoToJson(it)) }
        return array.toString()
    }

    private fun decodeFavorites(raw: String?): Map<String, ExplorePhoto> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val array = JSONArray(raw)
            val result = LinkedHashMap<String, ExplorePhoto>()
            for (i in 0 until array.length()) {
                val photo = photoFromJson(array.optJSONObject(i) ?: continue) ?: continue
                result[photo.id] = photo
            }
            result
        } catch (e: Exception) {
            // Corrupt/unreadable prefs value: start clean rather than crashing Explore.
            emptyMap()
        }
    }

    private fun encodeHistory(entries: List<HistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = photoToJson(entry.photo)
            obj.put(FIELD_VIEWED_AT, entry.viewedAtMillis)
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodeHistory(raw: String?): List<HistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<HistoryEntry>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val photo = photoFromJson(obj) ?: continue
                result += HistoryEntry(photo, obj.optLong(FIELD_VIEWED_AT))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun photoToJson(photo: ExplorePhoto): JSONObject = JSONObject().apply {
        put("id", photo.id)
        put("thumbnailUrl", photo.thumbnailUrl)
        put("referenceUrl", photo.referenceUrl)
        put("width", photo.width)
        put("height", photo.height)
        put("altText", photo.altText)
        put("photographerName", photo.photographerName)
        put("photographerUrl", photo.photographerUrl)
        put("sourceName", photo.sourceName)
        put("sourcePageUrl", photo.sourcePageUrl)
    }

    private fun photoFromJson(obj: JSONObject): ExplorePhoto? {
        val id = obj.optString("id")
        val thumbnailUrl = obj.optString("thumbnailUrl")
        if (id.isBlank() || thumbnailUrl.isBlank()) return null
        return ExplorePhoto(
            id = id,
            thumbnailUrl = thumbnailUrl,
            referenceUrl = obj.optString("referenceUrl").ifBlank { thumbnailUrl },
            width = obj.optInt("width"),
            height = obj.optInt("height"),
            altText = obj.optString("altText"),
            photographerName = obj.optString("photographerName"),
            photographerUrl = obj.optString("photographerUrl"),
            sourceName = obj.optString("sourceName"),
            sourcePageUrl = obj.optString("sourcePageUrl"),
        )
    }
}

/** One History entry: the photo that was opened, and when. */
data class HistoryEntry(val photo: ExplorePhoto, val viewedAtMillis: Long)
