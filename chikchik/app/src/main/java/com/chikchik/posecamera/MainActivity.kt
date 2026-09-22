package com.chikchik.posecamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chikchik.posecamera.ui.camera.CameraScreen
import com.chikchik.posecamera.ui.theme.ChikChikTheme

/**
 * Stage 4: live CameraX preview + reference-image overlay + camera controls
 * (switch camera, zoom, tap-to-focus, flash, timer) + photo capture & save.
 * (Capture uses CameraX ImageCapture; the overlay never ends up in the saved photo.)
 * Stage 5: modern minimal dark UI, portrait + landscape support.
 * Stage 6: error handling, memory/performance hardening, privacy (no INTERNET), release build config.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChikChikTheme {
                CameraScreen()
            }
        }
    }
}
