package com.chikchik.posecamera

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.chikchik.posecamera.ui.camera.CameraScreen
import com.chikchik.posecamera.ui.explore.ExploreScreen
import com.chikchik.posecamera.ui.theme.ChikChikTheme
import com.chikchik.posecamera.util.CameraPreferences
import com.chikchik.posecamera.util.LocaleHelper

/**
 * Stage 4: live CameraX preview + reference-image overlay + camera controls
 * (switch camera, zoom, tap-to-focus, flash, timer) + photo capture & save.
 * (Capture uses CameraX ImageCapture; the overlay never ends up in the saved photo.)
 * Stage 5: modern minimal dark UI, portrait + landscape support.
 * Stage 6: error handling, memory/performance hardening, privacy (no INTERNET), release build config.
 * Stage 15: added the Explore / Inspiration screen, reached from the camera's top bar.
 * Stage 20: Explore's "Use as Reference" is wired the same lightweight way - no navigation
 * graph, no separate reference system. See [ChikChikApp].
 * Stage 27: [attachBaseContext] applies whichever language the user picked in Settings (see
 * [LocaleHelper]), before anything on this Activity resolves a single string or layout
 * direction. When the user changes the language, [com.chikchik.posecamera.ui.camera.CameraScreen]
 * calls `recreate()`, which re-runs this same method with the new choice already saved.
 */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = CameraPreferences.getLanguage(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChikChikTheme {
                ChikChikApp()
            }
        }
    }
}

/**
 * The app has no Compose Navigation graph (by design, it has always been a single
 * screen); Explore is added the same lightweight way the camera/preview switch
 * already works in [com.chikchik.posecamera.ui.camera.CameraFlow] - a small piece
 * of state here decides which top-level screen is shown.
 *
 * Stage 20: "Use as Reference" is wired the same way, with one more small piece of state -
 * [pendingReferenceUrl] holds the URL of an Explore photo the user just picked. Setting it
 * also switches back to the camera; [CameraScreen] downloads that URL and feeds it into its
 * own existing reference state, then calls back to clear it. This file never touches a
 * Bitmap, a Uri or the reference/pose system itself - it only decides which screen is on top
 * and forwards one URL between them, exactly like [showExplore] already forwards a click.
 */
@Composable
private fun ChikChikApp() {
    var showExplore by rememberSaveable { mutableStateOf(false) }
    var pendingReferenceUrl by rememberSaveable { mutableStateOf<String?>(null) }

    if (showExplore) {
        ExploreScreen(
            onBack = { showExplore = false },
            onUseAsReference = { photo ->
                pendingReferenceUrl = photo.referenceUrl
                showExplore = false
            }
        )
    } else {
        CameraScreen(
            onExploreClick = { showExplore = true },
            pendingReferenceUrl = pendingReferenceUrl,
            onPendingReferenceHandled = { pendingReferenceUrl = null }
        )
    }
}
