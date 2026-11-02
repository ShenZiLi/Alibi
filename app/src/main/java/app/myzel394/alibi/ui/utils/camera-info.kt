package app.myzel394.alibi.ui.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import kotlin.math.abs

data class CameraInfo(
    val id: String,
    val lens: Lens,
    val equivalentFocalLength: Float? = null,
) {
    enum class Lens { BACK, FRONT, EXTERNAL, UNKNOWN }
    enum class LensKind { STANDARD, ULTRA_WIDE, TELEPHOTO }

    val lensKind: LensKind
        get() = when {
            lens != Lens.BACK || equivalentFocalLength == null -> LensKind.STANDARD
            equivalentFocalLength < 20f -> LensKind.ULTRA_WIDE
            equivalentFocalLength >= 45f -> LensKind.TELEPHOTO
            else -> LensKind.STANDARD
        }

    companion object {
        fun queryAvailableCameras(context: Context): List<CameraInfo> {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = manager.cameraIdList.mapNotNull { id ->
                runCatching { id to manager.getCameraCharacteristics(id) }.getOrNull()
            }.toMap()
            val cameras = characteristics.map { (id, info) ->
                fromCameraId(
                    id,
                    info.get(CameraCharacteristics.LENS_FACING) ?: -1,
                    info.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull(),
                    info.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width,
                )
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return cameras

            // A logical rear camera and its main physical sensor can both be listed.
            // Hide only a confirmed child with the same field of view; preserve the
            // ultra-wide/telephoto sensors and unrelated cameras with similar optics.
            return cameras.filterNot { child ->
                child.lens == Lens.BACK && child.lensKind == LensKind.STANDARD &&
                    child.equivalentFocalLength != null && cameras.any { parent ->
                        parent.id != child.id && parent.lens == child.lens &&
                            parent.equivalentFocalLength?.let {
                                abs(child.equivalentFocalLength / it - 1f) < 0.03f
                            } == true &&
                            child.id in characteristics.getValue(parent.id).physicalCameraIds
                    }
            }
        }

        fun fromCameraId(
            cameraId: String,
            lensFacing: Int,
            focalLength: Float? = null,
            sensorWidth: Float? = null,
        ) = CameraInfo(
            id = cameraId,
            lens = when (lensFacing) {
                CameraCharacteristics.LENS_FACING_BACK -> Lens.BACK
                CameraCharacteristics.LENS_FACING_FRONT -> Lens.FRONT
                CameraCharacteristics.LENS_FACING_EXTERNAL -> Lens.EXTERNAL
                else -> Lens.UNKNOWN
            },
            equivalentFocalLength = if (focalLength != null && focalLength > 0 &&
                sensorWidth != null && sensorWidth > 0
            ) focalLength * 36f / sensorWidth else null,
        )

        @OptIn(ExperimentalCamera2Interop::class)
        fun selectorForId(cameraId: String?): CameraSelector =
            if (cameraId == null) CameraSelector.DEFAULT_BACK_CAMERA
            else CameraSelector.Builder()
                .addCameraFilter { cameras ->
                    cameras.filter { Camera2CameraInfo.from(it).cameraId == cameraId }
                }
                .build()
    }
}
