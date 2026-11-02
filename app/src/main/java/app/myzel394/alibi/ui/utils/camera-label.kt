package app.myzel394.alibi.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.myzel394.alibi.R

@Composable
fun CameraInfo.cameraLabel(): String = stringResource(
    when (lens) {
        CameraInfo.Lens.FRONT -> R.string.ui_videoRecorder_action_start_settings_cameraLens_front_label
        CameraInfo.Lens.EXTERNAL -> R.string.ui_videoRecorder_action_start_settings_cameraLens_external_label
        CameraInfo.Lens.UNKNOWN -> R.string.ui_videoRecorder_action_start_settings_cameraLens_unknown_label
        CameraInfo.Lens.BACK -> when (lensKind) {
            CameraInfo.LensKind.ULTRA_WIDE -> R.string.ui_videoRecorder_camera_ultraWide
            CameraInfo.LensKind.TELEPHOTO -> R.string.ui_videoRecorder_camera_telephoto
            CameraInfo.LensKind.STANDARD -> R.string.ui_videoRecorder_camera_main
        }
    }
)
