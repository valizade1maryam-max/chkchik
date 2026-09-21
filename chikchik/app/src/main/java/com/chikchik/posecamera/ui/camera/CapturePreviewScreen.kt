package com.chikchik.posecamera.ui.camera

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chikchik.posecamera.R
import com.chikchik.posecamera.util.loadBitmap
import java.io.File

/**
 * Shown right after the shutter: displays the photo exactly as the camera
 * recorded it (no reference overlay, no UI) with Retake / Share / Save.
 *
 * The file is only a temporary capture; it reaches the gallery when Save is pressed.
 */
@Composable
fun CapturePreviewScreen(
    file: File,
    isSaving: Boolean,
    onRetake: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current

    var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(file) { mutableStateOf(true) }

    // Down-sampled copy for display only; the file on disk stays untouched.
    LaunchedEffect(file) {
        bitmap = loadBitmap(context, Uri.fromFile(file))?.asImageBitmap()
        loading = false
    }

    // System Back = Retake (discard the temp photo, return to the camera).
    // While saving, Back is swallowed so the app is not closed mid-save.
    BackHandler { if (!isSaving) onRetake() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap
            when {
                image != null -> Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                loading -> CircularProgressIndicator(color = Color.White)
                else -> Text(
                    text = stringResource(R.string.image_load_failed),
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRetake,
                enabled = !isSaving,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                ),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.retake)) }

            FilledTonalButton(
                onClick = onShare,
                enabled = !isSaving,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.share)) }

            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
