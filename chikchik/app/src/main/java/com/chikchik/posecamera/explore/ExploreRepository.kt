package com.chikchik.posecamera.explore

import com.chikchik.posecamera.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Stage 16: online source for the Explore screen. Stage 17 adds text search ([searchPhotos])
 * on top of the same source, reusing the same request/parsing plumbing. Stage 23 adds
 * [findSimilarPhotos], a third caller of that same plumbing for a photo's "Similar Ideas".
 *
 * Uses Pexels' official REST API (https://www.pexels.com/api/), a free, key-based API whose
 * license (https://www.pexels.com/license/) explicitly allows using its photos in apps like
 * this one. No scraping, no unofficial/undocumented calls, and no third-party HTTP/JSON
 * library was added - a plain HttpURLConnection GET plus org.json (already bundled with
 * Android) covers both the curated feed and the search endpoint.
 *
 * The API key is never hardcoded: it comes from [BuildConfig.PEXELS_API_KEY], generated at
 * build time from the git-ignored `secrets.properties` (see `secrets.properties.example` at
 * the project root). Without that file the key is an empty string and every call cleanly
 * returns [ExploreError.NOT_CONFIGURED] instead of doing a doomed network request.
 *
 * Everything runs on [Dispatchers.IO], so a slow/failed connection never blocks the UI
 * thread (Compose keeps rendering/animating while this suspends).
 *
 * Caching: the last successful curated page and the last successful search (for its exact
 * query string) are each kept in memory for the life of the process, so re-showing the same
 * feed/search in one app session does not repeat the network call. Neither is written to disk.
 */
object ExploreRepository {

    private const val CURATED_PHOTOS_URL = "https://api.pexels.com/v1/curated"
    private const val SEARCH_PHOTOS_URL = "https://api.pexels.com/v1/search"
    private const val PAGE_SIZE = 24
    /** Stage 23: "Similar Ideas" only ever needs a handful of suggestions (requirement 6). */
    private const val SIMILAR_PHOTOS_PAGE_SIZE = 8
    private const val REQUEST_TIMEOUT_MS = 12_000

    @Volatile
    private var cachedPhotos: List<ExplorePhoto>? = null

    @Volatile
    private var cachedSearchQuery: String? = null

    @Volatile
    private var cachedSearchPhotos: List<ExplorePhoto>? = null

    /**
     * Returns the cached page if there is one, unless [forceRefresh] is set (used by the
     * screen's "retry" button so the user isn't stuck replaying a stale failure/empty page).
     */
    suspend fun getCuratedPhotos(forceRefresh: Boolean = false): ExploreResult {
        cachedPhotos?.takeIf { !forceRefresh }?.let { return ExploreResult.Success(it) }

        val url = "$CURATED_PHOTOS_URL?per_page=$PAGE_SIZE&page=1"
        val result = fetchPhotos(url)
        if (result is ExploreResult.Success) cachedPhotos = result.photos
        return result
    }

    /**
     * Searches Pexels for [query] (e.g. "Mirror Selfie", "Gym Pose"). Returns the cached
     * result if the exact same query was just searched, unless [forceRefresh] is set (the
     * screen's "retry" button).
     *
     * A blank query always returns an empty success without making a request - the caller
     * (the Explore screen) is expected to fall back to [getCuratedPhotos] when there is no
     * search text, since this repository has no opinion on what an empty query should show.
     */
    suspend fun searchPhotos(query: String, forceRefresh: Boolean = false): ExploreResult {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return ExploreResult.Success(emptyList())

        if (!forceRefresh && cachedSearchQuery == trimmedQuery) {
            cachedSearchPhotos?.let { return ExploreResult.Success(it) }
        }

        val apiKey = BuildConfig.PEXELS_API_KEY
        if (apiKey.isBlank()) {
            return ExploreResult.Failure(ExploreError.NOT_CONFIGURED)
        }

        val encodedQuery = withContext(Dispatchers.IO) {
            URLEncoder.encode(trimmedQuery, StandardCharsets.UTF_8.name())
        }
        val url = "$SEARCH_PHOTOS_URL?query=$encodedQuery&per_page=$PAGE_SIZE&page=1"
        val result = fetchPhotos(url)
        if (result is ExploreResult.Success) {
            cachedSearchQuery = trimmedQuery
            cachedSearchPhotos = result.photos
        }
        return result
    }

    /**
     * Stage 23: "Similar Ideas" for [photo]'s detail view.
     *
     * Pexels has no related/similar-photos endpoint (only Search, Curated and "get one photo
     * by id") and no tags/category field on a photo - the only real, per-photo descriptive
     * data it gives is [ExplorePhoto.altText]. So "similar" here means requirement 4's
     * fallback: reuse the same Search endpoint with a query built from *this* photo's own
     * data (see [buildSimilarSearchQuery]) - never a fabricated category or a claimed pose
     * match with no real evidence behind it.
     *
     * Deliberately bypasses [searchPhotos]'s single-slot cache (that cache is for Explore's
     * own search box/filters - reusing it here could evict the feed's current results out
     * from under it) and requests only [SIMILAR_PHOTOS_PAGE_SIZE] photos, not [PAGE_SIZE], to
     * keep this a light, incidental request (requirement 6). The current photo is always
     * excluded from the result (requirement 5).
     *
     * Returns an empty success (not a failure) when [photo] simply has no usable text to
     * search by - that is a normal "nothing to suggest" outcome, not a network/server problem.
     */
    suspend fun findSimilarPhotos(photo: ExplorePhoto): ExploreResult {
        val query = buildSimilarSearchQuery(photo) ?: return ExploreResult.Success(emptyList())

        val apiKey = BuildConfig.PEXELS_API_KEY
        if (apiKey.isBlank()) return ExploreResult.Failure(ExploreError.NOT_CONFIGURED)

        val encodedQuery = withContext(Dispatchers.IO) {
            URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        }
        val url = "$SEARCH_PHOTOS_URL?query=$encodedQuery&per_page=$SIMILAR_PHOTOS_PAGE_SIZE&page=1"
        return when (val result = fetchPhotos(url)) {
            is ExploreResult.Success -> ExploreResult.Success(result.photos.filterNot { it.id == photo.id })
            is ExploreResult.Failure -> result
        }
    }

    /**
     * Builds the free-text query [findSimilarPhotos] searches with, from data [photo] actually
     * carries - never an invented category. [ExplorePhoto.altText] (Pexels' own description of
     * that exact photo) is the primary signal. [ExplorePhoto.detectedPoseCategory] (Stage 22;
     * always null today - see its doc comment) is folded in too when a future stage populates
     * it for real, since a confirmed pose category is a legitimate similarity signal, unlike a
     * guessed one. Returns null when neither is available, so the caller can report "no
     * suggestions" instead of searching on nothing.
     */
    private fun buildSimilarSearchQuery(photo: ExplorePhoto): String? {
        val terms = listOfNotNull(
            photo.altText.trim().takeIf { it.isNotBlank() },
            photo.detectedPoseCategory?.searchKeyword,
        )
        return terms.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    /** Shared GET + parse logic used by both [getCuratedPhotos] and [searchPhotos]. */
    private suspend fun fetchPhotos(urlString: String): ExploreResult {
        val apiKey = BuildConfig.PEXELS_API_KEY
        if (apiKey.isBlank()) {
            return ExploreResult.Failure(ExploreError.NOT_CONFIGURED)
        }

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", apiKey)
                    connectTimeout = REQUEST_TIMEOUT_MS
                    readTimeout = REQUEST_TIMEOUT_MS
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext ExploreResult.Failure(ExploreError.SERVER)
                }

                val body = connection.inputStream
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }

                ExploreResult.Success(parsePhotos(body))
            } catch (e: SocketTimeoutException) {
                ExploreResult.Failure(ExploreError.NETWORK)
            } catch (e: IOException) {
                // Covers "no connection" (UnknownHostException is an IOException subtype) too.
                ExploreResult.Failure(ExploreError.NETWORK)
            } catch (e: Exception) {
                // Unexpected response shape, etc. - not a connectivity problem.
                ExploreResult.Failure(ExploreError.SERVER)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parsePhotos(body: String): List<ExplorePhoto> {
        val photosArray: JSONArray = JSONObject(body).optJSONArray("photos") ?: JSONArray()
        val result = mutableListOf<ExplorePhoto>()
        for (i in 0 until photosArray.length()) {
            val photo = photosArray.optJSONObject(i) ?: continue
            val src = photo.optJSONObject("src") ?: continue
            val thumbnailUrl = src.optString("medium").ifBlank { src.optString("small") }
            if (thumbnailUrl.isBlank()) continue
            // Stage 20: prefer a larger rendition for "Use as Reference" (better pose-detection
            // input than the grid thumbnail); Pexels always includes "original" as a fallback.
            val referenceUrl = src.optString("large2x")
                .ifBlank { src.optString("large") }
                .ifBlank { src.optString("original") }
                .ifBlank { thumbnailUrl }

            result += ExplorePhoto(
                id = photo.optLong("id").toString(),
                thumbnailUrl = thumbnailUrl,
                referenceUrl = referenceUrl,
                width = photo.optInt("width"),
                height = photo.optInt("height"),
                altText = photo.optString("alt"),
                photographerName = photo.optString("photographer"),
                photographerUrl = photo.optString("photographer_url"),
                sourceName = "Pexels",
                sourcePageUrl = photo.optString("url"),
            )
        }
        return result
    }
}
