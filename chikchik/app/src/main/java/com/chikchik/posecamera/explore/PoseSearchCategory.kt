package com.chikchik.posecamera.explore

import androidx.annotation.StringRes
import com.chikchik.posecamera.R
import com.chikchik.posecamera.pose.PoseKeypointType

/**
 * Stage 22: the fixed set of simple pose categories a person can search Explore by, instead of
 * (or together with) typing free text - requirement 3's exact list.
 *
 * ChikChik already has a real, structured notion of a body pose - [PoseKeypointType] and
 * [com.chikchik.posecamera.pose.PoseSnapshot] from Stage 10 - it is just never run on Explore's
 * online photos: that would mean downloading and analyzing every single result, which this
 * stage deliberately does not do (see [ExploreRepository]'s doc comment on why Pexels has no
 * real Pose field to query). So each category here is named and grouped the same way that
 * on-device model already thinks about a body, purely so a later stage that *can* get real
 * per-photo pose data (running detection on downloaded photos, or a smarter source) can slot
 * straight into this exact taxonomy instead of inventing a new one or touching every caller:
 *
 * - [STANDING] / [SITTING]: hip/knee/ankle keypoints roughly straight vs. bent.
 * - [FULL_BODY] / [HALF_BODY]: ankle/knee keypoints present, or only the upper-body
 *   [PoseKeypointType.CORE] points down to hip/shoulder.
 * - [ONE_HAND] / [TWO_HANDS]: one vs. both of [PoseKeypointType.LEFT_WRIST] /
 *   [PoseKeypointType.RIGHT_WRIST] confidently visible/raised.
 * - [MIRROR_SELFIE]: a framing/context cue more than a pure keypoint shape - kept as its own
 *   category, the same way Stage 18's filter already treated it.
 * - [HEAD_FACE]: only head-region keypoints (HEAD, eyes, ears) matter; body keypoints are
 *   absent or irrelevant.
 *
 * Today [searchKeyword] is the *only* thing actually used: Pexels (ChikChik's only online image
 * source) has no Pose parameter to filter or sort by, so a category is applied the same way
 * Stage 18's Gender/Style filters already work - by folding this plain English term into the
 * same free-text query Explore's search box sends (see [ExploreRepository.searchPhotos]).
 * No photo is ever tagged with a category the source did not actually confirm - see
 * [ExplorePhoto.detectedPoseCategory] for the (currently always empty) field reserved for a
 * future stage that has real per-photo pose data to attach.
 */
enum class PoseSearchCategory(@StringRes val labelRes: Int, val searchKeyword: String) {
    STANDING(R.string.explore_filter_pose_standing, "Standing"),
    SITTING(R.string.explore_filter_pose_sitting, "Sitting"),
    FULL_BODY(R.string.explore_filter_pose_full_body, "Full Body"),
    HALF_BODY(R.string.explore_filter_pose_half_body, "Half Body"),
    ONE_HAND(R.string.explore_filter_pose_one_hand, "One Hand Pose"),
    TWO_HANDS(R.string.explore_filter_pose_two_hands, "Two Hands Pose"),
    MIRROR_SELFIE(R.string.explore_filter_pose_mirror_selfie, "Mirror Selfie"),
    HEAD_FACE(R.string.explore_filter_pose_head_face, "Face Portrait"),
}
