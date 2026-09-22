package com.chikchik.posecamera.util

import android.content.Context

/**
 * Stage 25: tiny persisted camera settings that must survive closing and reopening the app -
 * plain [android.content.SharedPreferences] (already used the same way by
 * [com.chikchik.posecamera.explore.ExploreLocalStore]), which is by far the simplest fit for a
 * single boolean flag and needs no new dependency.
 *
 * Reads/writes are synchronous but touch only a single already-parsed primitive, so unlike
 * [com.chikchik.posecamera.explore.ExploreLocalStore]'s JSON blobs this is safe to call directly
 * from the main/Compose thread - no IO dispatcher or loading state needed.
 */
object CameraPreferences {

    private const val PREFS_NAME = "camera_prefs"
    private const val KEY_GRID_ENABLED = "grid_enabled"
    private const val KEY_LANGUAGE = "app_language"

    /** Off by default, matching the app's general preference for maximum preview visibility. */
    fun isGridEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GRID_ENABLED, false)

    fun setGridEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GRID_ENABLED, enabled).apply()
    }

    /**
     * Stage 27: the language the user explicitly picked in Settings ("fa" or "en"), or null
     * when they have never picked one yet. Null is intentionally left as "no override" rather
     * than defaulted to "fa" here, so a device that has never opened Settings keeps exactly the
     * Stage 26 behavior (resources resolved from the system locale) - only an explicit choice
     * in Settings starts overriding it. See [com.chikchik.posecamera.util.LocaleHelper].
     */
    fun getLanguage(context: Context): String? = prefs(context).getString(KEY_LANGUAGE, null)

    fun setLanguage(context: Context, languageTag: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, languageTag).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
