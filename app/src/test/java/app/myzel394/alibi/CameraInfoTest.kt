package app.myzel394.alibi

import app.myzel394.alibi.ui.utils.CameraInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraInfoTest {
    @Test
    fun rearCameraZeroUsesItsReportedFacing() {
        assertEquals(CameraInfo.Lens.BACK, CameraInfo.fromCameraId("0", 1).lens)
    }

    @Test
    fun frontCameraOneUsesItsReportedFacing() {
        assertEquals(CameraInfo.Lens.FRONT, CameraInfo.fromCameraId("1", 0).lens)
    }

    @Test
    fun cameraIdentifiersRemainOpaqueStrings() {
        assertEquals("aux-tele", CameraInfo.fromCameraId("aux-tele", 1).id)
    }
}
