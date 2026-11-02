package app.myzel394.alibi.ui.components.RecorderScreen.molecules

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.myzel394.alibi.R
import app.myzel394.alibi.db.AppSettings
import app.myzel394.alibi.ui.components.RecorderScreen.atoms.BigButton
import app.myzel394.alibi.ui.components.atoms.PermissionRequester
import app.myzel394.alibi.ui.models.VideoRecorderModel
import app.myzel394.alibi.ui.utils.CameraInfo
import app.myzel394.alibi.ui.utils.PermissionHelper

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRecordingStart(
    videoRecorder: VideoRecorderModel,
    appSettings: AppSettings,
    onHideAudioRecording: () -> Unit,
    onShowAudioRecording: () -> Unit,
    showPreview: Boolean,
    useLargeButtons: Boolean? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameras by remember(context) {
        mutableStateOf(runCatching { CameraInfo.queryAvailableCameras(context) }.getOrDefault(emptyList()))
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameras = runCatching { CameraInfo.queryAvailableCameras(context) }
                    .getOrDefault(emptyList())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(cameras) {
        if (cameras.none { it.id == videoRecorder.cameraID }) {
            videoRecorder.cameraID = (cameras.firstOrNull { it.lens == CameraInfo.Lens.BACK }
                ?: cameras.firstOrNull())?.id
        }
    }

    var showSheet by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSheet) {
        VideoRecorderPreparationSheet(
            showPreview = showPreview,
            videoSettings = videoRecorder,
            onDismiss = {
                showSheet = false
            },
            onPreviewVisible = onHideAudioRecording,
            onPreviewHidden = onShowAudioRecording,
            onStartRecording = {
                videoRecorder.startRecording(context, appSettings)
            },
        )
    }

    PermissionRequester(
        permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
        onPermissionAvailable = {
            showSheet = true
        }
    ) { triggerExternalStorage ->
        Column(
            modifier = Modifier.widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BigButton(
                label = stringResource(R.string.ui_videoRecorder_action_start_label),
                description = stringResource(R.string.ui_videoRecorder_action_configure_label),
                icon = Icons.Default.CameraAlt,
                onLongClick = {
                    if (appSettings.requiresExternalStoragePermission(context)) {
                        triggerExternalStorage()
                        return@BigButton
                    }

                    showSheet = true
                },
                onClick = {
                    if (appSettings.requiresExternalStoragePermission(context)) {
                        triggerExternalStorage()
                        return@BigButton
                    }

                    if (PermissionHelper.hasGranted(
                            context,
                            Manifest.permission.CAMERA
                        ) && PermissionHelper.hasGranted(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        )
                    ) {
                        videoRecorder.startRecording(context, appSettings)
                    } else {
                        showSheet = true
                    }
                },
                isBig = useLargeButtons,
            )
            if (!showPreview) {
                QuickCameraSelector(cameras = cameras, videoRecorder = videoRecorder)
            }
        }
    }
}
