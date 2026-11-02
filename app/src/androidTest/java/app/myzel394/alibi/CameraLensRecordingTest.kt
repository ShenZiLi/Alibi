package app.myzel394.alibi

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myzel394.alibi.ui.utils.CameraInfo
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class CameraLensRecordingTest {
    @Test
    fun logicalMainIsNotRepeatedAsPhysicalMain() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameras = CameraInfo.queryAvailableCameras(context)
        cameras.filter { it.lens == CameraInfo.Lens.BACK &&
            it.lensKind == CameraInfo.LensKind.STANDARD }.forEach { parent ->
            val physicalIds = manager.getCameraCharacteristics(parent.id).physicalCameraIds
            val focalLength = parent.equivalentFocalLength ?: return@forEach
            val duplicates = cameras.filter { child ->
                child.id in physicalIds && child.lens == parent.lens &&
                    child.equivalentFocalLength?.let { abs(it / focalLength - 1f) < 0.03f } == true
            }
            assertTrue("Repeated main cameras: ${parent.id} and ${duplicates.map { it.id }}",
                duplicates.isEmpty())
        }
    }

    @Test
    @OptIn(ExperimentalCamera2Interop::class)
    fun exposedRearLensesRecordAndPreviewUsingTheirExactIds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
        val provider = ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)
        val cameras = CameraInfo.queryAvailableCameras(context).filter {
            it.lens == CameraInfo.Lens.BACK
        }
        assertTrue("No rear cameras exposed", cameras.isNotEmpty())
        ActivityScenario.launch(CameraTestActivity::class.java).use { scenario ->
            cameras.forEach { info ->
                val streaming = CountDownLatch(1)
                val started = CountDownLatch(1)
                val finalized = CountDownLatch(1)
                val result = AtomicReference<VideoRecordEvent.Finalize>()
                val file = File.createTempFile("lens-${info.id}-", ".mp4", context.cacheDir)
                var recording: androidx.camera.video.Recording? = null
                try {
                    scenario.onActivity { activity ->
                        val view = PreviewView(activity).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        activity.setContentView(view)
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(view.surfaceProvider)
                        }
                        view.previewStreamState.observe(activity) {
                            if (it == PreviewView.StreamState.STREAMING) streaming.countDown()
                        }
                        val capture = VideoCapture.withOutput(Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HIGHEST)).build())
                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            activity, CameraInfo.selectorForId(info.id), preview, capture
                        )
                        assertEquals(info.id, Camera2CameraInfo.from(camera.cameraInfo).cameraId)
                        recording = capture.output.prepareRecording(
                            context, FileOutputOptions.Builder(file).build()
                        ).start(ContextCompat.getMainExecutor(context)) { event ->
                            if (event is VideoRecordEvent.Start) started.countDown()
                            if (event is VideoRecordEvent.Finalize) {
                                result.set(event)
                                finalized.countDown()
                            }
                        }
                    }
                    assertTrue("No preview for camera ${info.id}", streaming.await(15, TimeUnit.SECONDS))
                    assertTrue("No recording for camera ${info.id}", started.await(15, TimeUnit.SECONDS))
                    SystemClock.sleep(2000)
                    scenario.onActivity { recording?.stop() }
                    assertTrue(finalized.await(10, TimeUnit.SECONDS))
                    val event = result.get()
                    assertFalse("Camera ${info.id}: recording error ${event.error}", event.hasError())
                    assertTrue(event.recordingStats.recordedDurationNanos > 1_000_000_000L)
                    assertTrue(file.length() > 0)
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("stream", "Camera ${info.id} (${info.lensKind}): preview and recording passed, ${file.length()} bytes\n")
                    })
                } finally {
                    scenario.onActivity {
                        recording?.close()
                        provider.unbindAll()
                    }
                    file.delete()
                }
            }
        }
    }
}
