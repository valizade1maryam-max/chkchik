package com.chikchik.posecamera.explore

/**
 * Stage 16: data model for one inspiration photo returned by [ExploreRepository].
 *
 * This file only defines the shape of the data (mirrors the split already used in
 * `pose/PoseKeypoint.kt` vs `pose/PoseDetector.kt`); [ExploreRepository] fills it in.
 *
 * Besides what is needed to show the photo, every field that identifies where it came
 * from is kept ([sourceName], [sourcePageUrl], [photographerName], [photographerUrl]) so a
 * later stage can display proper attribution / open the original page without having to
 * change this model or re-fetch anything.
 */
data class ExplorePhoto(
    /** Id from the source API; stable enough to use as a LazyGrid key. */
    val id: String,
    /** A server-resized (small/medium) image URL - right-sized for a grid thumbnail. */
    val thumbnailUrl: String,
    /**
     * Stage 20: server-resized (large) image URL used when this photo is picked as a
     * Reference Image, so the same pose-detection pipeline gets a sharper source than the
     * small grid thumbnail. Falls back to [thumbnailUrl] when the source has nothing bigger.
     */
    val referenceUrl: String = thumbnailUrl,
    /** Original photo dimensions, used only to size the thumbnail's aspect ratio. */
    val width: Int,
    val height: Int,
    /** Short description of the photo, if the source provides one (used as alt text). */
    val altText: String,
    val photographerName: String,
    val photographerUrl: String,
    /** e.g. "Pexels" - shown next to attribution in a later stage. */
    val sourceName: String,
    /** Link to the photo's page on the source site (for "open original" in a later stage). */
    val sourcePageUrl: String,
    /**
     * Stage 22: reserved for a future stage that can attach a real, source-confirmed
     * [PoseSearchCategory] to this specific photo (e.g. by running on-device detection on it,
     * or from a source that provides real pose metadata). Always null today - Pose Search
     * (Stage 22) only ever searches by keyword (see [PoseSearchCategory]'s doc comment) and
     * never claims to know a returned photo's actual pose, so this field is not populated yet.
     */
    val detectedPoseCategory: PoseSearchCategory? = null,
)

/** Why a fetch attempt failed, so the UI can show a message that matches the actual cause. */
enum class ExploreError {
    /** No API key configured yet - see `secrets.properties.example` at the project root. */
    NOT_CONFIGURED,
    /** Device offline, host unreachable, or the request timed out. */
    NETWORK,
    /** A response came back, but something about it (auth, rate limit, ...) was wrong. */
    SERVER,
}

/** Outcome of an [ExploreRepository] fetch. */
sealed interface ExploreResult {
    data class Success(val photos: List<ExplorePhoto>) : ExploreResult
    data class Failure(val error: ExploreError) : ExploreResult
}
