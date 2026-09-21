package com.chikchik.posecamera.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ZoomState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import com.chikchik.posecamera.R
import com.chikchik.posecamera.ui.overlay.OverlayImage
import com.chikchik.posecamera.ui.overlay.OverlayState
import com.chikchik.posecamera.util.OVERLAY_MAX_SIDE
import com.chikchik.posecamera.util.clearCaptureCache
import com.chikchik.posecamera.util.loadBitmap
import com.chikchik.posecamera.util.saveJpegToGallery
import com.chikchik.posecamera.util.shareJpeg
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Main screen (stage 5): live camera + reference-image overlay + camera controls
 * (flash, timer, zoom, camera switch, tap-to-focus) + shutter, then a preview screen
 * (Retake / Share / Save) for the captured photo.
 *
 * The UI is a minimal dark camera interface: the top row holds Back / Flash /
 * Timer / Camera switch, the bottom holds the reference-image picker, its
 * opacity/lock/reset controls, the zoom slider and a large shutter button.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    // Ask once automatically on first launch. `permissionRequested` is saved across
    // rotation, so turning the phone never re-opens the system dialog.
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted && !permissionRequested) {
            permissionRequested = true
            cameraPermission.launchPermissionRequest()
        }
    }

    if (!cameraPermission.status.isGranted) {
        PermissionRequest(
            onClick = {
                if (permissionRequested && !cameraPermission.status.shouldShowRationale) {
                    // Permanently denied -> only the system settings can fix it.
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.open_settings_failed, Toast.LENGTH_LONG).show()
                    }
                } else {
                    permissionRequested = true
                    cameraPermission.launchPermissionRequest()
                }
            }
        )
        return
    }

    CameraFlow()
}

@Composable
private fun PermissionRequest(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.camera_permission_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.camera_permission_button))
        }
    }
}

/**
 * Owns all state that must survive switching between the camera and the
 * preview screen (reference image, its transform, the captured photo), so
 * "Retake" returns to the camera with the reference overlay exactly as it was.
 */
@Composable
private fun CameraFlow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Overlay source image (Uri survives config change / process recreation) and its transform.
    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val overlayState = rememberSaveable(saver = OverlayState.Saver) { OverlayState() }
    var overlayImage by remember { mutableStateOf<ImageBitmap?>(null) }

    // Path of the photo that was just captured (temp file) and is waiting in the preview screen.
    var capturedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Camera settings live here (not inside the camera screen) so they survive
    // going to the preview screen and back with "Retake".
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var timerSeconds by rememberSaveable { mutableIntStateOf(0) }

    // Android Photo Picker (no storage permission needed).
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            overlayState.reset()
            overlayState.locked = false
            imageUri = uri
        }
    }

    // Decode the chosen image off the main thread.
    LaunchedEffect(imageUri) {
        val uri = imageUri
        if (uri == null) {
            overlayImage = null
        } else {
            val bitmap = loadBitmap(context, uri, maxSide = OVERLAY_MAX_SIDE)
            if (bitmap == null) {
                Toast.makeText(context, R.string.image_load_failed, Toast.LENGTH_LONG).show()
                imageUri = null
                overlayImage = null
            } else {
                overlayImage = bitmap.asImageBitmap()
            }
        }
    }

    // Remove temp photos left over from an earlier session.
    LaunchedEffect(Unit) {
        clearCaptureCache(context, keep = capturedPath?.let { File(it) })
    }

    fun discardCapture() {
        capturedPath?.let { File(it).delete() }
        capturedPath = null
    }

    fun saveCapture() {
        val path = capturedPath ?: return
        if (isSaving) return
        isSaving = true
        scope.launch {
            val file = File(path)
            val savedUri = saveJpegToGallery(context, file)
            isSaving = false
            if (savedUri != null) {
                file.delete()
                capturedPath = null
                Toast.makeText(context, R.string.photo_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Android 9 and below still need the classic storage permission to write into Pictures/.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            saveCapture()
        } else {
            Toast.makeText(context, R.string.storage_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    val path = capturedPath
    if (path == null) {
        CameraWithOverlay(
            overlayImage = overlayImage,
            overlayState = overlayState,
            lensFacing = lensFacing,
            onLensFacingChange = { lensFacing = it },
            flashMode = flashMode,
            onFlashModeChange = { flashMode = it },
            timerSeconds = timerSeconds,
            onTimerSecondsChange = { timerSeconds = it },
            onPickImage = {
                try {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } catch (e: Exception) {
                    // No app on the device can pick images.
                    Toast.makeText(context, R.string.gallery_unavailable, Toast.LENGTH_SHORT).show()
                }
            },
            onPhotoCaptured = { file -> capturedPath = file.absolutePath }
        )
    } else {
        val file = remember(path) { File(path) }
        CapturePreviewScreen(
            file = file,
            isSaving = isSaving,
            onRetake = { discardCapture() },
            onShare = {
                if (!shareJpeg(context, file, context.getString(R.string.share_chooser_title))) {
                    Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
                }
            },
            onSave = {
                val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                if (needsLegacyPermission) {
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    saveCapture()
                }
            }
        )
    }
}

/** Timer choices in seconds; 0 = off. */
private val TIMER_OPTIONS = listOf(0, 3, 5, 10)

private fun nextFlashMode(mode: Int): Int = when (mode) {
    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
    else -> ImageCapture.FLASH_MODE_OFF
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraWithOverlay(
    overlayImage: ImageBitmap?,
    overlayState: OverlayState,
    lensFacing: Int,
    onLensFacingChange: (Int) -> Unit,
    flashMode: Int,
    onFlashModeChange: (Int) -> Unit,
    timerSeconds: Int,
    onTimerSecondsChange: (Int) -> Unit,
    onPickImage: () -> Unit,
    onPhotoCaptured: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // A fresh ImageCapture each time the camera screen is shown.
    val imageCapture = remember { buildImageCapture() }
    var isCapturing by remember { mutableStateOf(false) }

    // The camera that is currently bound (null while switching lenses).
    var boundCamera by remember { mutableStateOf<BoundCamera?>(null) }
    var hasFlash by remember { mutableStateOf(false) }
    var canSwitchLens by remember { mutableStateOf(false) }
    // True while no camera can be used (missing, in use by another app, disabled, ...).
    var cameraError by remember { mutableStateOf(false) }

    // Timer countdown: 0 = not counting.
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    val counting = countdown > 0
    val busy = counting || isCapturing

    // If the app goes to the background during the countdown, cancel it: the camera is
    // released then, and taking a picture "in the background" would only fail.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) countdownJob?.cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Small menu (Bottom Sheet) for choosing the timer duration - keeps the
    // top bar to a single icon instead of a row of duration options.
    var showTimerSheet by remember { mutableStateOf(false) }
    val timerSheetState = rememberModalBottomSheetState()

    // Tap-to-focus ring (UI only).
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(900)
            focusPoint = null
        }
    }

    // Camera zoom, mirrored from CameraX's ZoomState. This only ever zooms the
    // camera; the reference overlay has its own separate transform.
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    DisposableEffect(boundCamera, lifecycleOwner) {
        val liveData = boundCamera?.camera?.cameraInfo?.zoomState
        val observer = Observer<ZoomState> { state ->
            zoomRatio = state.zoomRatio
            linearZoom = state.linearZoom
            minZoomRatio = state.minZoomRatio
            maxZoomRatio = state.maxZoomRatio
        }
        if (liveData != null) {
            liveData.observe(lifecycleOwner, observer)
        } else {
            zoomRatio = 1f
            linearZoom = 0f
        }
        onDispose { liveData?.removeObserver(observer) }
    }
    val canZoom = boundCamera != null && maxZoomRatio - minZoomRatio > 0.05f

    // Runtime camera problems reported by CameraX (e.g. camera used by another app, disabled
    // by policy). Cleared automatically as soon as the camera is open again.
    DisposableEffect(boundCamera, lifecycleOwner) {
        val stateData = boundCamera?.camera?.cameraInfo?.cameraState
        val stateObserver = Observer<CameraState> { state ->
            cameraError = state.error != null && state.type != CameraState.Type.OPEN
        }
        stateData?.observe(lifecycleOwner, stateObserver)
        onDispose { stateData?.removeObserver(stateObserver) }
    }

    // Flash: a camera without a flash unit always uses OFF.
    val effectiveFlash = if (hasFlash) flashMode else ImageCapture.FLASH_MODE_OFF
    LaunchedEffect(effectiveFlash, imageCapture) {
        imageCapture.flashMode = effectiveFlash
    }

    fun capture() {
        if (isCapturing) return
        isCapturing = true
        // Records the real camera frame via ImageCapture - never a screenshot.
        takePhoto(
            context = context,
            imageCapture = imageCapture,
            onCaptured = { file ->
                isCapturing = false
                onPhotoCaptured(file)
            },
            onFailure = {
                isCapturing = false
                Toast.makeText(context, R.string.capture_failed, Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun onShutterClick() {
        if (counting) {
            // Shutter doubles as "cancel" while the countdown is running.
            countdownJob?.cancel()
            return
        }
        if (timerSeconds <= 0) {
            capture()
            return
        }
        countdownJob = scope.launch {
            try {
                for (second in timerSeconds downTo 1) {
                    countdown = second
                    delay(1000)
                }
            } finally {
                // Also runs on cancel; a cancelled countdown never reaches capture().
                countdown = 0
                countdownJob = null
            }
            capture()
        }
    }

    fun onFocusTap(offset: Offset) {
        // Only show the ring if the device accepted the focus request.
        if (boundCamera?.focusAt(offset.x, offset.y) == true) {
            focusPoint = offset
        }
    }
    val currentOnFocusTap by rememberUpdatedState(::onFocusTap)

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {

        // Layer 1: live camera (CameraX Preview + ImageCapture).
        CameraPreview(
            imageCapture = imageCapture,
            lensFacing = lensFacing,
            onCameraError = { cameraError = true },
            onCameraChanged = { cam ->
                boundCamera = cam
                if (cam != null) {
                    cameraError = false
                    hasFlash = cam.hasFlash
                    canSwitchLens = cam.canSwitchLens
                    // The requested lens may not exist on this device.
                    if (cam.lensFacing != lensFacing) onLensFacingChange(cam.lensFacing)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 1.5: catches taps for tap-to-focus when no reference image is shown.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset -> currentOnFocusTap(offset) }
                }
        )

        // Layer 2: reference image (separate Compose layer, NOT part of the camera image).
        // It sits above the camera, so it forwards plain taps for tap-to-focus.
        overlayImage?.let { image ->
            OverlayImage(
                image = image,
                state = overlayState,
                onTap = { offset -> currentOnFocusTap(offset) }
            )
        }

        // Layer 3: UI only - none of this is ever part of the photo.

        // Focus ring.
        focusPoint?.let { point ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            color = Color.White,
                            radius = 36.dp.toPx(),
                            center = point,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
            )
        }

        // Timer countdown number.
        if (counting) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(140.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdown.toString(),
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Camera unavailable: a plain message instead of a mysterious black screen.
        if (cameraError) {
            Text(
                text = stringResource(R.string.camera_unavailable),
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp)
            )
        }

        // Top bar: Back | Flash | Timer | Camera switch.
        TopBar(
            onBack = { (context as? Activity)?.finish() },
            hasFlash = hasFlash,
            flashMode = effectiveFlash,
            onFlashClick = { onFlashModeChange(nextFlashMode(flashMode)) },
            busy = busy,
            timerSeconds = timerSeconds,
            onTimerClick = { showTimerSheet = true },
            canSwitchLens = canSwitchLens,
            onSwitchLensClick = {
                onLensFacingChange(
                    if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                )
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Bottom: overlay controls (when a reference image is set), zoom slider
        // (when the camera supports it), then the reference button + shutter.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = overlayImage != null,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                OverlayQuickBar(state = overlayState, modifier = Modifier.fillMaxWidth())
            }

            AnimatedVisibility(
                visible = canZoom,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                ZoomQuickBar(
                    zoomRatio = zoomRatio,
                    linearZoom = linearZoom,
                    onLinearZoomChange = { boundCamera?.setLinearZoom(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    ReferenceButton(
                        hasImage = overlayImage != null,
                        enabled = !busy,
                        onClick = onPickImage
                    )
                }

                ShutterButton(
                    enabled = counting || (boundCamera != null && !isCapturing),
                    counting = counting,
                    onClick = ::onShutterClick
                )

                Box(modifier = Modifier.weight(1f))
            }
        }
    }

    if (showTimerSheet) {
        TimerSheet(
            sheetState = timerSheetState,
            selected = timerSeconds,
            onSelect = { seconds ->
                onTimerSecondsChange(seconds)
                scope.launch { timerSheetState.hide() }.invokeOnCompletion {
                    if (!timerSheetState.isVisible) showTimerSheet = false
                }
            },
            onDismiss = { showTimerSheet = false }
        )
    }
}

private fun formatZoom(ratio: Float): String = String.format(Locale.US, "%.1f×", ratio)

/** Circular translucent icon button used across the top bar. */
@Composable
private fun TopIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    badgeText: String? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.35f)
            highlighted -> accent
            else -> Color.White
        },
        label = "topIconTint"
    )

    val button: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    enabled = enabled,
                    onClickLabel = contentDescription,
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }

    Box(modifier = modifier) {
        if (badgeText != null) {
            BadgedBox(
                badge = {
                    Badge(containerColor = accent, contentColor = Color.Black) {
                        Text(badgeText)
                    }
                }
            ) { button() }
        } else {
            button()
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    hasFlash: Boolean,
    flashMode: Int,
    onFlashClick: () -> Unit,
    busy: Boolean,
    timerSeconds: Int,
    onTimerClick: () -> Unit,
    canSwitchLens: Boolean,
    onSwitchLensClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TopIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.back),
            onClick = onBack
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val flashIcon = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                else -> Icons.Filled.FlashOff
            }
            TopIconButton(
                icon = flashIcon,
                contentDescription = stringResource(R.string.flash),
                onClick = onFlashClick,
                enabled = hasFlash && !busy,
                highlighted = flashMode != ImageCapture.FLASH_MODE_OFF
            )

            TopIconButton(
                icon = Icons.Filled.Timer,
                contentDescription = stringResource(R.string.timer),
                onClick = onTimerClick,
                enabled = !busy,
                highlighted = timerSeconds > 0,
                badgeText = if (timerSeconds > 0) timerSeconds.toString() else null
            )

            TopIconButton(
                icon = Icons.Filled.FlipCameraAndroid,
                contentDescription = stringResource(R.string.switch_camera),
                onClick = onSwitchLensClick,
                enabled = canSwitchLens && !busy
            )
        }
    }
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    counting: Boolean,
    onClick: () -> Unit,
) {
    val target = when {
        counting -> Color(0xFFFF5A4F) // tap again to cancel the countdown
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.4f)
    }
    val inner by animateColorAsState(targetValue = target, label = "shutterColor")
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(inner)
            .clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.shutter),
                role = Role.Button,
                onClick = onClick
            )
    )
}

/** Reference-image picker button; its label reflects whether an image is already set. */
@Composable
private fun ReferenceButton(
    hasImage: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoLibrary,
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(if (hasImage) R.string.change_reference else R.string.pick_reference),
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}

/**
 * Opacity slider plus clearly-visible Lock/Unlock and Reset controls for the
 * reference-image overlay. Shown only once a reference image is set, and
 * always directly on screen (not tucked into a menu) since these are the
 * controls the user needs while positioning the overlay.
 */
@Composable
private fun OverlayQuickBar(
    state: OverlayState,
    modifier: Modifier = Modifier,
) {
    val onPanel = Color.White
    val accent = MaterialTheme.colorScheme.primary
    val lockTint by animateColorAsState(
        targetValue = if (state.locked) accent else onPanel,
        label = "lockTint"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Opacity,
            contentDescription = stringResource(R.string.opacity),
            tint = onPanel.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
        Slider(
            value = state.opacity,
            onValueChange = { state.opacity = it },
            valueRange = 0.05f..1f,
            colors = SliderDefaults.colors(
                thumbColor = onPanel,
                activeTrackColor = onPanel,
                inactiveTrackColor = onPanel.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Text(
            text = "${(state.opacity * 100).roundToInt()}%",
            color = onPanel,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(end = 2.dp)
        )

        // Lock / unlock: filled icon + accent tint when locked, so the state is unmistakable.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (state.locked) accent.copy(alpha = 0.18f) else Color.Transparent)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(if (state.locked) R.string.locked else R.string.lock),
                    onClick = { state.locked = !state.locked }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (state.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = stringResource(if (state.locked) R.string.locked else R.string.lock),
                tint = lockTint,
                modifier = Modifier.size(20.dp)
            )
        }

        // Reset: disabled while locked, since it would move the locked image.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = !state.locked,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.reset),
                    onClick = { state.reset() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.RestartAlt,
                contentDescription = stringResource(R.string.reset),
                tint = if (state.locked) onPanel.copy(alpha = 0.3f) else onPanel,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Slim always-visible zoom slider, shown only while the camera supports zoom. */
@Composable
private fun ZoomQuickBar(
    zoomRatio: Float,
    linearZoom: Float,
    onLinearZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onPanel = Color.White
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ZoomIn,
            contentDescription = stringResource(R.string.zoom),
            tint = onPanel.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
        Slider(
            value = linearZoom.coerceIn(0f, 1f),
            onValueChange = onLinearZoomChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = onPanel,
                activeTrackColor = onPanel,
                inactiveTrackColor = onPanel.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Text(
            text = formatZoom(zoomRatio),
            color = onPanel,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** Small Bottom Sheet used to pick the timer duration, keeping the top bar to a single icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerSheet(
    sheetState: SheetState,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.choose_timer),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            TIMER_OPTIONS.forEach { seconds ->
                val label = if (seconds <= 0) {
                    stringResource(R.string.timer_off)
                } else {
                    stringResource(R.string.timer_seconds, seconds)
                }
                val isSelected = seconds == selected
                val accent = MaterialTheme.colorScheme.primary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable(role = Role.Button) { onSelect(seconds) }
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
