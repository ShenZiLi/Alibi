package app.myzel394.alibi.ui.components.RecorderScreen.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.R
import app.myzel394.alibi.ui.models.VideoRecorderModel
import app.myzel394.alibi.ui.utils.CameraInfo
import app.myzel394.alibi.ui.utils.cameraLabel

@Composable
fun QuickCameraSelector(cameras: List<CameraInfo>, videoRecorder: VideoRecorderModel) {
    if (cameras.isEmpty()) return
    val orderedCameras = remember(cameras) {
        cameras.sortedWith(compareBy<CameraInfo> {
            when (it.lens) {
                CameraInfo.Lens.FRONT -> 0
                CameraInfo.Lens.BACK -> 1
                CameraInfo.Lens.EXTERNAL -> 2
                CameraInfo.Lens.UNKNOWN -> 3
            }
        }.thenBy {
            when (it.lensKind) {
                CameraInfo.LensKind.STANDARD -> 0
                CameraInfo.LensKind.TELEPHOTO -> 1
                CameraInfo.LensKind.ULTRA_WIDE -> 2
            }
        })
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        items(orderedCameras, key = { it.id }) { camera ->
            val label = when (camera.lens) {
                CameraInfo.Lens.BACK -> stringResource(when (camera.lensKind) {
                    CameraInfo.LensKind.STANDARD -> R.string.ui_videoRecorder_camera_short_main
                    CameraInfo.LensKind.ULTRA_WIDE -> R.string.ui_videoRecorder_camera_short_ultraWide
                    CameraInfo.LensKind.TELEPHOTO -> R.string.ui_videoRecorder_camera_short_telephoto
                })
                CameraInfo.Lens.FRONT -> stringResource(R.string.ui_videoRecorder_camera_short_front)
                else -> camera.cameraLabel()
            }
            FilterChip(
                selected = videoRecorder.cameraID == camera.id,
                onClick = { videoRecorder.cameraID = camera.id },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
