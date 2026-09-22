package com.chikchik.posecamera.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbAuto
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.WbShade
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import android.util.Log
import com.chikchik.posecamera.BuildConfig
import com.chikchik.posecamera.R
import com.chikchik.posecamera.pose.LivePoseAnalyzer
import com.chikchik.posecamera.pose.PoseDetectionResult
import com.chikchik.posecamera.pose.PoseSnapshot
import com.chikchik.posecamera.pose.comparePoses
import com.chikchik.posecamera.pose.detectPose
import com.chikchik.posecamera.pose.mirrorPoseSnapshot
import com.chikchik.posecamera.ui.overlay.OverlayImage
import com.chikchik.posecamera.ui.overlay.OverlayState
import com.chikchik.posecamera.util.OVERLAY_MAX_SIDE
import com.chikchik.posecamera.util.CameraPreferences
import com.chikchik.posecamera.util.clearCaptureCache
import com.chikchik.posecamera.util.openTelegramContact
import com.chikchik.posecamera.util.downloadExploreReferenceImage
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
 *
 * Stage 20: [pendingReferenceUrl] carries an Explore photo picked with "Use as Reference"
 * (set by [com.chikchik.posecamera.MainActivity] once Explore closes). It is downloaded and
 * fed into the exact same reference Uri/state the gallery picker uses - see [CameraFlow].
 * [onPendingReferenceHandled] clears it once handled (success or failure) so it is not
 * re-applied on the next recomposition/rotation.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onExploreClick: () -> Unit = {},
    pendingReferenceUrl: String? = null,
    onPendingReferenceHandled: () -> Unit = {},
) {
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

    CameraFlow(
        onExploreClick = onExploreClick,
        pendingReferenceUrl = pendingReferenceUrl,
        onPendingReferenceHandled = onPendingReferenceHandled,
    )
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
 *
 * Stage 20: [pendingReferenceUrl] (an Explore photo's URL) is handled the same way as
 * [imageUri] below, just with one extra step first - it is downloaded to a local file (see
 * [downloadExploreReferenceImage]) so it becomes a Uri, then assigned to [imageUri] exactly
 * like the Photo Picker's callback does. From that point on it is indistinguishable from any
 * other reference photo: the same decode/down-sample/pose-detection effect further down picks
 * it up, and Retake/lock/opacity/reset all keep working unchanged.
 */
@Composable
private fun CameraFlow(
    onExploreClick: () -> Unit,
    pendingReferenceUrl: String? = null,
    onPendingReferenceHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Overlay source image (Uri survives config change / process recreation) and its transform.
    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val overlayState = rememberSaveable(saver = OverlayState.Saver) { OverlayState() }
    var overlayImage by remember { mutableStateOf<ImageBitmap?>(null) }
    // Stage 20: true while an Explore-picked reference is being downloaded (see the
    // LaunchedEffect(pendingReferenceUrl) below). Only ever true for that one download.
    var isLoadingExploreReference by remember { mutableStateOf(false) }
    // Stage 10: body pose extracted from the current reference photo (null = no reference
    // photo yet, or detection has not finished). Read by CameraWithOverlay (Stage 12) to
    // compare against the live camera pose.
    var referencePose by remember { mutableStateOf<PoseDetectionResult?>(null) }

    // Path of the photo that was just captured (temp file) and is waiting in the preview screen.
    var capturedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Camera settings live here (not inside the camera screen) so they survive
    // going to the preview screen and back with "Retake".
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    // Camera2 id of the chosen physical lens for the current facing (Ultra-Wide, Telephoto,
    // Macro, ...), or null to use the device's own default lens for that facing.
    var lensOptionId by rememberSaveable { mutableStateOf<String?>(null) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var timerSeconds by rememberSaveable { mutableIntStateOf(0) }
    // Continuous flashlight - independent from the still-capture flash mode above.
    var torchOn by rememberSaveable { mutableStateOf(false) }
    // Camera2 CONTROL_AWB_MODE override, or null to use the device's own default (Auto).
    var whiteBalanceMode by rememberSaveable { mutableStateOf<Int?>(null) }
    // false = 4:3 (sensor-native on almost every device), true = 16:9.
    var aspectRatioWide by rememberSaveable { mutableStateOf(false) }
    // Stage 25: composition grid over the live preview only. Persisted across app restarts
    // via SharedPreferences (rememberSaveable alone would not survive a real app close), read
    // once when this Composable first enters, then kept in sync on every toggle.
    var gridEnabled by remember { mutableStateOf(CameraPreferences.isGridEnabled(context)) }
    // Stage 27: which language chip should show as selected. Reflects the user's explicit
    // choice if there is one (CameraPreferences.getLanguage), otherwise falls back to whatever
    // language is actually showing right now (the system-resolved one, same as Stage 26) so the
    // sheet never opens with neither chip highlighted. Does not itself change any resource - the
    // actual override only happens once the user taps a chip (see onLanguageChange below).
    var currentLanguage by remember {
        mutableStateOf(
            CameraPreferences.getLanguage(context)
                ?: if (Locale.getDefault().language == "en") "en" else "fa"
        )
    }

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
            referencePose = null
        } else {
            val bitmap = loadBitmap(context, uri, maxSide = OVERLAY_MAX_SIDE)
            if (bitmap == null) {
                Toast.makeText(context, R.string.image_load_failed, Toast.LENGTH_LONG).show()
                imageUri = null
                overlayImage = null
                referencePose = null
            } else {
                overlayImage = bitmap.asImageBitmap()
                // Stage 10: extract body keypoints from the reference photo. Runs once per
                // picked photo (not per frame), on-device, and never crashes on a photo with
                // no person / an unclear pose - it just yields a non-SUCCESS status.
                val poseResult = detectPose(bitmap)
                referencePose = poseResult
                Log.d(
                    "ChikChikPose",
                    "Reference pose: status=${poseResult.status}, " +
                        "coreKeypoints=${poseResult.primaryPose?.detectedCoreCount ?: 0}/14, " +
                        "confidence=${poseResult.primaryPose?.overallConfidence ?: 0f}"
                )
            }
        }
    }

    // Stage 20: an Explore photo was picked with "Use as Reference". Download it once, then
    // route it through the exact same `imageUri` state the Photo Picker uses above - this is
    // the only place Explore and the reference system connect.
    LaunchedEffect(pendingReferenceUrl) {
        val url = pendingReferenceUrl
        if (url != null) {
            isLoadingExploreReference = true
            val uri = downloadExploreReferenceImage(context, url)
            isLoadingExploreReference = false
            if (uri != null) {
                overlayState.reset()
                overlayState.locked = false
                imageUri = uri
            } else {
                Toast.makeText(
                    context,
                    R.string.explore_reference_download_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
            onPendingReferenceHandled()
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
        Box(modifier = Modifier.fillMaxSize()) {
            CameraWithOverlay(
                onExploreClick = onExploreClick,
                overlayImage = overlayImage,
                overlayState = overlayState,
                referencePose = referencePose,
                lensFacing = lensFacing,
                onLensFacingChange = { facing ->
                    lensFacing = facing
                    // Physical lens ids belong to one facing; the other facing picks its own default.
                    lensOptionId = null
                },
                lensOptionId = lensOptionId,
                onLensOptionIdChange = { lensOptionId = it },
                flashMode = flashMode,
                onFlashModeChange = { flashMode = it },
                timerSeconds = timerSeconds,
                onTimerSecondsChange = { timerSeconds = it },
                torchOn = torchOn,
                onTorchChange = { torchOn = it },
                whiteBalanceMode = whiteBalanceMode,
                onWhiteBalanceModeChange = { whiteBalanceMode = it },
                aspectRatioWide = aspectRatioWide,
                onAspectRatioWideChange = { aspectRatioWide = it },
                gridEnabled = gridEnabled,
                onGridEnabledChange = { enabled ->
                    gridEnabled = enabled
                    CameraPreferences.setGridEnabled(context, enabled)
                },
                currentLanguage = currentLanguage,
                onLanguageChange = { language ->
                    currentLanguage = language
                    CameraPreferences.setLanguage(context, language)
                    (context as? Activity)?.recreate()
                },
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
            // Stage 20: brief scrim while the Explore-picked reference downloads. Purely
            // visual - it does not block/replace any of CameraWithOverlay's own state or logic.
            if (isLoadingExploreReference) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
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

/** Stage 28: Telegram username opened by the "Contact Me" row in Settings. */
private const val CONTACT_TELEGRAM_USERNAME = "ChikyChikyBoomBoomm"

private fun nextFlashMode(mode: Int): Int = when (mode) {
    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
    else -> ImageCapture.FLASH_MODE_OFF
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraWithOverlay(
    onExploreClick: () -> Unit,
    overlayImage: ImageBitmap?,
    overlayState: OverlayState,
    // Stage 10's extracted pose for the current reference photo (null = no reference photo, or
    // detection not finished yet). Compared against the live pose below.
    referencePose: PoseDetectionResult?,
    lensFacing: Int,
    onLensFacingChange: (Int) -> Unit,
    lensOptionId: String?,
    onLensOptionIdChange: (String?) -> Unit,
    flashMode: Int,
    onFlashModeChange: (Int) -> Unit,
    timerSeconds: Int,
    onTimerSecondsChange: (Int) -> Unit,
    torchOn: Boolean,
    onTorchChange: (Boolean) -> Unit,
    whiteBalanceMode: Int?,
    onWhiteBalanceModeChange: (Int?) -> Unit,
    aspectRatioWide: Boolean,
    onAspectRatioWideChange: (Boolean) -> Unit,
    gridEnabled: Boolean,
    onGridEnabledChange: (Boolean) -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPhotoCaptured: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // A fresh ImageCapture whenever the chosen aspect ratio changes; CameraPreview already
    // re-binds on every ImageCapture identity change, so this alone triggers the rebind.
    val imageCapture = remember(aspectRatioWide) {
        buildImageCapture(if (aspectRatioWide) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3)
    }
    var isCapturing by remember { mutableStateOf(false) }

    // Stage 11: live pose detected from the camera feed (null = no person / not detected yet),
    // plus the analyzed frame's own pixel size (needed to line the overlay up with the
    // preview). Compared against the reference pose below (Stage 12); not yet turned into any
    // on-screen guidance - that is for a later stage.
    var livePose by remember { mutableStateOf<PoseSnapshot?>(null) }
    var livePoseImageWidth by remember { mutableIntStateOf(0) }
    var livePoseImageHeight by remember { mutableIntStateOf(0) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val poseAnalyzer = remember {
        LivePoseAnalyzer(mainExecutor = mainExecutor) { result, width, height ->
            livePose = result.primaryPose
            livePoseImageWidth = width
            livePoseImageHeight = height
        }
    }
    DisposableEffect(Unit) {
        onDispose { poseAnalyzer.close() }
    }
    // A pose from the previous lens/facing has a different frame size and no longer applies -
    // drop it so a stale skeleton never lingers on screen while the new camera comes up.
    LaunchedEffect(lensFacing, lensOptionId) {
        livePose = null
    }

    // Stage 12: compare the reference photo's pose against whatever is currently live. `remember`
    // keyed on both snapshots means this only recomputes when either pose actually changes value
    // (i.e. at most once per throttled live-pose update from LivePoseAnalyzer) - not on every
    // recomposition - so it never adds per-frame cost on top of the camera preview/shutter.
    // Turned into on-screen text guidance below (Stage 13).
    // Stage 24: when the reference image is flipped on screen, compare against the mirrored
    // version of its pose (x reflected, left/right swapped) so "left"/"right" guidance still
    // matches what the user actually sees - detection itself is never re-run on a flip.
    val referenceSnapshot = referencePose?.primaryPose?.let { snapshot ->
        if (overlayState.flipped) mirrorPoseSnapshot(snapshot) else snapshot
    }
    val poseComparison = remember(referenceSnapshot, livePose) {
        comparePosesOrNull(referenceSnapshot, livePose)
    }
    // Logged only when the overall bucket actually changes (not on every throttled update), so
    // this stays useful for verifying Stage 12 without flooding logcat.
    LaunchedEffect(poseComparison?.overallStatus) {
        poseComparison?.let {
            Log.d(
                "ChikChikPoseCompare",
                "overall=${it.overallStatus}(${it.overallDifference}) " +
                    "head=${it.head.status} leftArm=${it.leftArm.status} rightArm=${it.rightArm.status} " +
                    "torso=${it.torso.status} leftLeg=${it.leftLeg.status} rightLeg=${it.rightLeg.status}"
            )
        }
    }

    // Stage 13: turns the comparison above into one plain-language instruction at a time
    // ("raise your right hand a little", "pose ready", ...). Only meaningful once a reference
    // pose exists - PoseGuidanceBar renders nothing for GuidanceInstruction.None.
    val guidanceInstruction = rememberPoseGuidanceInstruction(referenceSnapshot, livePose, poseComparison)

    // Stage 14: visual guidance (skeleton coloring, direction arrow, overall status), derived
    // from the exact same poseComparison/guidanceInstruction above - never a second decision
    // system, so it can never disagree with the Stage 13 text.
    val poseVisualState = rememberPoseVisualState(poseComparison, guidanceInstruction)

    // The camera that is currently bound (null while switching lenses).
    var boundCamera by remember { mutableStateOf<BoundCamera?>(null) }
    var hasFlash by remember { mutableStateOf(false) }
    var canSwitchLens by remember { mutableStateOf(false) }
    // Physical lenses (Ultra-Wide, Telephoto, Macro, ...) the device offers for the
    // current facing, discovered from the device's own camera list.
    var lensOptions by remember { mutableStateOf<List<LensOption>>(emptyList()) }
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

    // Bottom Sheet for the less-frequently-used manual controls (exposure, white balance,
    // manual focus, aspect ratio) - keeps the top bar to a single icon for all of them.
    var showAdvancedSheet by remember { mutableStateOf(false) }
    val advancedSheetState = rememberModalBottomSheetState()

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

    // Torch (continuous flashlight), mirrored from CameraX's TorchState so the UI reflects
    // what the device actually did (it may reject torch briefly, e.g. while overheated).
    var torchState by remember { mutableIntStateOf(TorchState.OFF) }
    DisposableEffect(boundCamera, lifecycleOwner) {
        val liveData = boundCamera?.camera?.cameraInfo?.torchState
        val observer = Observer<Int> { state -> torchState = state }
        if (liveData != null) {
            liveData.observe(lifecycleOwner, observer)
        } else {
            torchState = TorchState.OFF
        }
        onDispose { liveData?.removeObserver(observer) }
    }
    // Re-apply the user's torch preference whenever the bound camera changes (new lens/facing).
    LaunchedEffect(boundCamera, torchOn) {
        boundCamera?.setTorch(torchOn)
    }

    // Exposure compensation: CameraX-native, offered only when the bound camera actually
    // supports it and has more than a single step to move between.
    var exposureIndex by remember { mutableIntStateOf(0) }
    val exposureRange = boundCamera?.exposureState?.exposureCompensationRange
    val canAdjustExposure = boundCamera?.exposureState?.isExposureCompensationSupported == true &&
        exposureRange != null && exposureRange.upper > exposureRange.lower
    LaunchedEffect(boundCamera) {
        exposureIndex = boundCamera?.exposureState?.exposureCompensationIndex ?: 0
    }

    // Manual white balance override (Camera2 CONTROL_AWB_MODE). Reset if the newly bound
    // camera (different lens/facing) does not report the previously chosen mode as available.
    val whiteBalanceOptions = boundCamera?.availableWhiteBalanceModes
        ?.filter { it != CameraCharacteristics.CONTROL_AWB_MODE_OFF }
        ?: emptyList()
    LaunchedEffect(boundCamera) {
        if (whiteBalanceMode != null && whiteBalanceMode !in whiteBalanceOptions) {
            onWhiteBalanceModeChange(null)
        }
    }
    LaunchedEffect(boundCamera, whiteBalanceMode) {
        if (whiteBalanceMode != null && whiteBalanceMode in whiteBalanceOptions) {
            boundCamera?.setWhiteBalanceMode(whiteBalanceMode)
        }
    }

    // Manual focus distance override (Camera2 LENS_FOCUS_DISTANCE). Local to this camera
    // session only - switching lens/facing always goes back to normal auto-focus, since a
    // manual distance from one lens has no meaning on another.
    var manualFocusEnabled by remember { mutableStateOf(false) }
    var manualFocusValue by remember { mutableFloatStateOf(0f) }
    val canManualFocus = boundCamera?.hasManualFocus == true
    LaunchedEffect(boundCamera) {
        manualFocusEnabled = false
        manualFocusValue = 0f
    }
    LaunchedEffect(boundCamera, manualFocusEnabled) {
        val camera = boundCamera ?: return@LaunchedEffect
        if (manualFocusEnabled) {
            camera.setManualFocusDistance(manualFocusValue)
        } else {
            camera.clearManualFocus()
        }
    }

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

        // Layer 1: live camera (CameraX Preview + ImageCapture + Stage 11's pose ImageAnalysis).
        CameraPreview(
            imageCapture = imageCapture,
            lensFacing = lensFacing,
            lensOptionId = lensOptionId,
            poseAnalyzer = poseAnalyzer,
            onCameraError = { cameraError = true },
            onCameraChanged = { cam ->
                boundCamera = cam
                if (cam != null) {
                    cameraError = false
                    hasFlash = cam.hasFlash
                    canSwitchLens = cam.canSwitchLens
                    lensOptions = cam.lensOptions
                    // The requested lens may not exist on this device.
                    if (cam.lensFacing != lensFacing) onLensFacingChange(cam.lensFacing)
                    if (cam.activeLensOptionId != lensOptionId) onLensOptionIdChange(cam.activeLensOptionId)
                } else {
                    lensOptions = emptyList()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 1.05 (Stage 25): optional composition grid, camera preview only - never part of
        // the captured photo (separate Compose layer, same as the reference/pose overlays
        // below), never intercepts touches, and drawn before the reference image and pose
        // overlay so it can never sit on top of or shift either of them.
        if (gridEnabled) {
            CameraGridOverlay(modifier = Modifier.fillMaxSize())
        }

        // Layer 1.5: catches taps for tap-to-focus when no reference image is shown.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset -> currentOnFocusTap(offset) }
                }
        )

        // Layer 1.6 (Stage 11): live pose keypoints - simple, temporary visualization only, no
        // comparison/guidance yet. Purely visual (no pointerInput), so taps still pass through
        // to the tap-to-focus layer above.
        livePose?.let { pose ->
            PoseOverlay(
                pose = pose,
                imageWidth = livePoseImageWidth,
                imageHeight = livePoseImageHeight,
                mirrorX = lensFacing == CameraSelector.LENS_FACING_FRONT,
                visualState = poseVisualState,
                modifier = Modifier.fillMaxSize()
            )
        }

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

        // Top bar: Back | Explore | Flash | Timer | Camera switch.
        TopBar(
            onBack = { (context as? Activity)?.finish() },
            onExploreClick = onExploreClick,
            hasFlash = hasFlash,
            flashMode = effectiveFlash,
            onFlashClick = { onFlashModeChange(nextFlashMode(flashMode)) },
            busy = busy,
            torchOn = torchState == TorchState.ON,
            onTorchClick = { onTorchChange(!torchOn) },
            hasAdvancedControls = boundCamera != null,
            onAdvancedControlsClick = { showAdvancedSheet = true },
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

        // Stage 13/14: pose guidance, just below the top bar so it never collides with the
        // (already busy) bottom controls. Stage 14's short overall-status line sits above
        // Stage 13's specific instruction - one small text group, not a separate panel.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PoseStatusBadge(visualState = poseVisualState)
            PoseGuidanceBar(instruction = guidanceInstruction)
        }

        // Bottom: overlay controls (when a reference image is set), zoom slider
        // (when the camera supports it), then the reference button + shutter.
        // Stage 29: tighter outer padding/gaps (was vertical 12dp, gap 10dp) so this whole
        // stack - which sits directly over the lower body/reference - covers less of it.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = overlayImage != null,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                OverlayQuickBar(state = overlayState, modifier = Modifier.fillMaxWidth())
            }

            AnimatedVisibility(
                visible = lensOptions.size > 1,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                LensQuickBar(
                    options = lensOptions,
                    activeId = lensOptionId,
                    enabled = !busy,
                    onSelect = onLensOptionIdChange,
                    modifier = Modifier.fillMaxWidth()
                )
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

    if (showAdvancedSheet) {
        AdvancedControlsSheet(
            sheetState = advancedSheetState,
            aspectRatioWide = aspectRatioWide,
            onAspectRatioWideChange = onAspectRatioWideChange,
            gridEnabled = gridEnabled,
            onGridEnabledChange = onGridEnabledChange,
            currentLanguage = currentLanguage,
            onLanguageChange = onLanguageChange,
            canAdjustExposure = canAdjustExposure,
            exposureIndex = exposureIndex,
            exposureRange = exposureRange,
            exposureStep = boundCamera?.exposureState?.exposureCompensationStep,
            onExposureIndexChange = { index ->
                exposureIndex = index
                boundCamera?.setExposureCompensationIndex(index)
            },
            whiteBalanceOptions = whiteBalanceOptions,
            whiteBalanceMode = whiteBalanceMode,
            onWhiteBalanceModeChange = onWhiteBalanceModeChange,
            canManualFocus = canManualFocus,
            manualFocusEnabled = manualFocusEnabled,
            onManualFocusEnabledChange = { manualFocusEnabled = it },
            manualFocusValue = manualFocusValue,
            onContactMeClick = { openTelegramContact(context, CONTACT_TELEGRAM_USERNAME) },
            manualFocusMaxDiopters = boundCamera?.manualFocusMaxDiopters ?: 0f,
            onManualFocusValueChange = { value ->
                manualFocusValue = value
                boundCamera?.setManualFocusDistance(value)
            },
            onDismiss = { showAdvancedSheet = false }
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

    // Stage 29: 40dp (was 44dp) - a touch was shaved off every top-bar icon so the row
    // intrudes less on the preview, while staying comfortably tappable.
    val button: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(40.dp)
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
                modifier = Modifier.size(20.dp)
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
    onExploreClick: () -> Unit,
    hasFlash: Boolean,
    flashMode: Int,
    onFlashClick: () -> Unit,
    busy: Boolean,
    torchOn: Boolean,
    onTorchClick: () -> Unit,
    hasAdvancedControls: Boolean,
    onAdvancedControlsClick: () -> Unit,
    timerSeconds: Int,
    onTimerClick: () -> Unit,
    canSwitchLens: Boolean,
    onSwitchLensClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stage 29: tighter row padding/spacing (was horizontal 12/vertical 8, gap 8dp) to match
    // the smaller TopIconButton above and keep the whole bar as thin as possible.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                onClick = onBack
            )

            // Stage 15: entry point into the Explore / Inspiration screen.
            TopIconButton(
                icon = Icons.Filled.AutoAwesome,
                contentDescription = stringResource(R.string.explore),
                onClick = onExploreClick
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                icon = if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                contentDescription = stringResource(R.string.torch),
                onClick = onTorchClick,
                enabled = hasFlash && !busy,
                highlighted = torchOn
            )

            TopIconButton(
                icon = Icons.Filled.Timer,
                contentDescription = stringResource(R.string.timer),
                onClick = onTimerClick,
                enabled = !busy,
                highlighted = timerSeconds > 0,
                badgeText = if (timerSeconds > 0) timerSeconds.toString() else null
            )

            if (hasAdvancedControls) {
                TopIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.camera_settings),
                    onClick = onAdvancedControlsClick,
                    enabled = !busy
                )
            }

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
    // Stage 29: vertical padding trimmed from 12dp to 10dp - a little less height next to
    // the (unchanged, always fully-sized) shutter button.
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
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

    // Stage 29: this bar sits directly above the reference/body area whenever a reference
    // image is set, so its padding/icon sizes were trimmed a bit further than the others
    // below (was start 14/end 6/top+bottom 8dp, gap 4dp, circles 40dp) - the slider, its
    // label and every control are all still present and just as tappable, only smaller.
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Opacity,
            contentDescription = stringResource(R.string.opacity),
            tint = onPanel.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
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

        // Stage 24: horizontal flip toggle for the reference image only - never the live
        // camera feed. Works the same regardless of whether the reference came from the
        // Gallery or from Explore, since both feed into the same OverlayState/overlayImage.
        val flipTint by animateColorAsState(
            targetValue = if (state.flipped) accent else onPanel,
            label = "flipTint"
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (state.flipped) accent.copy(alpha = 0.18f) else Color.Transparent)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.flip_horizontal),
                    onClick = { state.flipped = !state.flipped }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Flip,
                contentDescription = stringResource(
                    if (state.flipped) R.string.flipped else R.string.flip_horizontal
                ),
                tint = flipTint,
                modifier = Modifier.size(18.dp)
            )
        }

        // Lock / unlock: filled icon + accent tint when locked, so the state is unmistakable.
        Box(
            modifier = Modifier
                .size(34.dp)
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
                modifier = Modifier.size(18.dp)
            )
        }

        // Reset: disabled while locked, since it would move the locked image.
        Box(
            modifier = Modifier
                .size(34.dp)
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
                modifier = Modifier.size(18.dp)
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
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.ZoomIn,
            contentDescription = stringResource(R.string.zoom),
            tint = onPanel.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
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

/** Localized label for a physical lens, shown on its selector chip. */
@Composable
private fun lensLabel(option: LensOption): String = when (option.type) {
    LensType.ULTRA_WIDE -> stringResource(R.string.lens_ultrawide)
    LensType.TELEPHOTO -> stringResource(R.string.lens_telephoto)
    LensType.MACRO -> stringResource(R.string.lens_macro)
    LensType.MAIN -> stringResource(R.string.lens_main)
    LensType.OTHER -> formatZoom(option.intrinsicZoomRatio)
}

/**
 * Row of chips for switching between the device's own physical lenses (Ultra-Wide,
 * Main, Telephoto, Macro, ...) for the current facing. Only shown when the device
 * actually offers more than one - this never simulates a lens with digital zoom.
 */
@Composable
private fun LensQuickBar(
    options: List<LensOption>,
    activeId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onPanel = Color.White
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val selected = option.cameraId == activeId
            val tint by animateColorAsState(
                targetValue = when {
                    !enabled -> onPanel.copy(alpha = 0.35f)
                    selected -> accent
                    else -> onPanel
                },
                label = "lensChipTint"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = lensLabel(option),
                        onClick = { onSelect(option.cameraId) }
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = lensLabel(option),
                    color = tint,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

/** Localized label for a Camera2 CONTROL_AWB_MODE value. */
@Composable
private fun whiteBalanceLabel(mode: Int): String = when (mode) {
    CameraCharacteristics.CONTROL_AWB_MODE_AUTO -> stringResource(R.string.wb_auto)
    CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT -> stringResource(R.string.wb_incandescent)
    CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT -> stringResource(R.string.wb_fluorescent)
    CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT -> stringResource(R.string.wb_warm_fluorescent)
    CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT -> stringResource(R.string.wb_daylight)
    CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> stringResource(R.string.wb_cloudy)
    CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT -> stringResource(R.string.wb_twilight)
    CameraCharacteristics.CONTROL_AWB_MODE_SHADE -> stringResource(R.string.wb_shade)
    else -> stringResource(R.string.wb_other)
}

/** Small icon for a Camera2 CONTROL_AWB_MODE value, purely decorative. */
private fun whiteBalanceIcon(mode: Int): ImageVector = when (mode) {
    CameraCharacteristics.CONTROL_AWB_MODE_AUTO -> Icons.Filled.WbAuto
    CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT,
    CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT -> Icons.Filled.WbIncandescent
    CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT -> Icons.Filled.WbSunny
    CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> Icons.Filled.WbCloudy
    CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT -> Icons.Filled.WbTwilight
    CameraCharacteristics.CONTROL_AWB_MODE_SHADE -> Icons.Filled.WbShade
    else -> Icons.Filled.WbAuto
}

/** A single row title inside [AdvancedControlsSheet]. */
@Composable
private fun SheetSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

/**
 * Bottom Sheet for the manual camera controls that do not need to be always on screen:
 * exposure compensation, white balance, manual focus and capture aspect ratio. Every
 * section is shown only when the currently bound camera actually supports it - a device
 * or lens without a given capability simply never shows that section.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdvancedControlsSheet(
    sheetState: SheetState,
    aspectRatioWide: Boolean,
    onAspectRatioWideChange: (Boolean) -> Unit,
    gridEnabled: Boolean,
    onGridEnabledChange: (Boolean) -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    canAdjustExposure: Boolean,
    exposureIndex: Int,
    exposureRange: android.util.Range<Int>?,
    exposureStep: android.util.Rational?,
    onExposureIndexChange: (Int) -> Unit,
    whiteBalanceOptions: List<Int>,
    whiteBalanceMode: Int?,
    onWhiteBalanceModeChange: (Int?) -> Unit,
    canManualFocus: Boolean,
    manualFocusEnabled: Boolean,
    onManualFocusEnabledChange: (Boolean) -> Unit,
    manualFocusValue: Float,
    manualFocusMaxDiopters: Float,
    onManualFocusValueChange: (Float) -> Unit,
    onContactMeClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary

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
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.camera_settings),
                style = MaterialTheme.typography.titleMedium
            )

            // Aspect ratio: always a real, hardware-native choice of output size.
            SheetSectionTitle(stringResource(R.string.aspect_ratio))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChip(
                    label = stringResource(R.string.ratio_4_3),
                    selected = !aspectRatioWide,
                    onClick = { onAspectRatioWideChange(false) }
                )
                ChoiceChip(
                    label = stringResource(R.string.ratio_16_9),
                    selected = aspectRatioWide,
                    onClick = { onAspectRatioWideChange(true) }
                )
            }

            // Stage 25: composition grid over the live preview only - a plain ON/OFF choice,
            // same chip style as Aspect Ratio above. Persisted by the caller (CameraFlow),
            // this sheet only reflects/toggles the current value.
            SheetSectionTitle(stringResource(R.string.grid))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChip(
                    label = stringResource(R.string.grid_on),
                    selected = gridEnabled,
                    onClick = { onGridEnabledChange(true) }
                )
                ChoiceChip(
                    label = stringResource(R.string.grid_off),
                    selected = !gridEnabled,
                    onClick = { onGridEnabledChange(false) }
                )
            }

            // Stage 27: app language - same chip style/pattern as the sections above. Picking
            // one persists it (CameraPreferences.setLanguage) and recreates the Activity so the
            // new language/RTL-LTR direction applies immediately (see onLanguageChange).
            SheetSectionTitle(stringResource(R.string.language_settings_title))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChip(
                    label = stringResource(R.string.language_option_persian),
                    selected = currentLanguage == "fa",
                    onClick = { onLanguageChange("fa") }
                )
                ChoiceChip(
                    label = stringResource(R.string.language_option_english),
                    selected = currentLanguage == "en",
                    onClick = { onLanguageChange("en") }
                )
            }

            if (canAdjustExposure && exposureRange != null) {
                SheetSectionTitle(stringResource(R.string.exposure))
                val step = exposureStep?.toFloat()?.takeIf { it > 0f } ?: 1f
                val evValue = exposureIndex * step
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Exposure,
                        contentDescription = stringResource(R.string.exposure),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Slider(
                        value = exposureIndex.toFloat(),
                        onValueChange = { onExposureIndexChange(it.roundToInt()) },
                        valueRange = exposureRange.lower.toFloat()..exposureRange.upper.toFloat(),
                        steps = (exposureRange.upper - exposureRange.lower - 1).coerceAtLeast(0),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "%+.1f", evValue),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
            }

            if (whiteBalanceOptions.isNotEmpty()) {
                SheetSectionTitle(stringResource(R.string.white_balance))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    whiteBalanceOptions.forEach { mode ->
                        ChoiceChip(
                            label = whiteBalanceLabel(mode),
                            icon = whiteBalanceIcon(mode),
                            selected = whiteBalanceMode == mode ||
                                (whiteBalanceMode == null && mode == CameraCharacteristics.CONTROL_AWB_MODE_AUTO),
                            onClick = { onWhiteBalanceModeChange(mode) }
                        )
                    }
                }
            }

            if (canManualFocus) {
                SheetSectionTitle(stringResource(R.string.focus_mode))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceChip(
                        label = stringResource(R.string.focus_auto),
                        icon = Icons.Filled.CenterFocusWeak,
                        selected = !manualFocusEnabled,
                        onClick = { onManualFocusEnabledChange(false) }
                    )
                    ChoiceChip(
                        label = stringResource(R.string.focus_manual),
                        icon = Icons.Filled.CenterFocusStrong,
                        selected = manualFocusEnabled,
                        onClick = { onManualFocusEnabledChange(true) }
                    )
                }
                AnimatedVisibility(visible = manualFocusEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.focus_near),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Slider(
                            value = manualFocusValue.coerceIn(0f, manualFocusMaxDiopters),
                            onValueChange = onManualFocusValueChange,
                            valueRange = 0f..manualFocusMaxDiopters,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                        Text(
                            text = stringResource(R.string.focus_far),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Stage 28: "Contact Me" - opens a Telegram chat (app if installed, web version
            // otherwise; see util/ContactUtils.kt). Same section-title pattern as the rest of
            // this sheet, but rendered as a single tappable row instead of a chip choice since
            // there is nothing to select between.
            SheetSectionTitle(stringResource(R.string.contact_me_title))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.contact_me_title),
                        onClick = onContactMeClick
                    )
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.contact_me_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.contact_me_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Stage 31: "About ChikChik" - app name + version only, same section-title and row
            // layout as Contact Me above, but purely informational (no click action).
            SheetSectionTitle(stringResource(R.string.about_app_title))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.about_app_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Small filled/outlined selectable chip shared by the sheets above. */
@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(50)
            )
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge
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
