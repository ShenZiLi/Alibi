package app.myzel394.alibi.ui.components.RecorderScreen.molecules

import CameraSelectionButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.myzel394.alibi.R
import app.myzel394.alibi.ui.models.VideoRecorderModel
import app.myzel394.alibi.ui.utils.CameraInfo
import app.myzel394.alibi.ui.utils.cameraLabel
import kotlin.math.roundToInt

@Composable
fun CamerasSelection(cameras: Iterable<CameraInfo>, videoSettings: VideoRecorderModel) {
    Column {
        cameras.forEach { camera ->
            CameraSelectionButton(
                cameraID = camera.lens,
                selected = videoSettings.cameraID == camera.id,
                onSelected = { videoSettings.cameraID = camera.id },
                label = camera.cameraLabel(),
                description = stringResource(
                    R.string.ui_videoRecorder_action_start_settings_cameraLens_label, camera.id
                ) + (camera.equivalentFocalLength?.let {
                    " · ${it.roundToInt()} mm"
                } ?: ""),
            )
        }
    }
}
