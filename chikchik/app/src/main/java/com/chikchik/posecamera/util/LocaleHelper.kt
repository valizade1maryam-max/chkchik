package com.chikchik.posecamera.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Stage 27: applies the language the user explicitly picked in Settings, using the plain
 * Configuration/Context override that has been the standard Android way to do this since long
 * before per-app language APIs existed - no new dependency, no AppCompatActivity requirement.
 *
 * [com.chikchik.posecamera.MainActivity] calls [wrap] from `attachBaseContext`, which is also
 * the standard place for this: it runs before any resource lookup for that Activity instance,
 * so every string/layout-direction resolution afterward (including RTL for "fa") already sees
 * the chosen language. When the user changes the language, the Activity is recreated (see
 * [com.chikchik.posecamera.ui.camera.CameraScreen]'s language chips) so `attachBaseContext` runs
 * again and picks up the new choice; that recreation preserves `rememberSaveable` state exactly
 * like a rotation does, so it never touches Explore's Favorites/History or any other stored data.
 */
object LocaleHelper {

    /**
     * Returns [context] unchanged when [languageTag] is null (Stage 26 behavior: resources
     * resolve from the system locale, nothing overridden), or a context configured for that
     * language otherwise.
     */
    fun wrap(context: Context, languageTag: String?): Context {
        if (languageTag.isNullOrEmpty()) return context

        val locale = Locale(languageTag)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }
}
