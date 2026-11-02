package app.myzel394.alibi.services

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Range
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.myzel394.alibi.NotificationHelper
import app.myzel394.alibi.db.RecordingInformation
import app.myzel394.alibi.enums.RecorderState
import app.myzel394.alibi.helpers.BatchesFolder
import app.myzel394.alibi.helpers.VideoBatchesFolder
import app.myzel394.alibi.ui.SUPPORTS_SAVING_VIDEOS_IN_CUSTOM_FOLDERS
import app.myzel394.alibi.ui.SUPPORTS_SCOPED_STORAGE
import app.myzel394.alibi.ui.utils.CameraInfo
import app.myzel394.alibi.ui.components.RecorderScreen.atoms.FloatingVideoPreview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.properties.Delegates

class VideoRecorderService :
    IntervalRecorderService<RecordingInformation, VideoBatchesFolder>() {
    override var batchesFolder = VideoBatchesFolder.viaInternalFolder(this)

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val _preview = MutableStateFlow<Preview?>(null)
    val preview = _preview.asStateFlow()
    private var floatingPreview: FloatingVideoPreview? = null
    private val _isFloatingPreviewVisible = MutableStateFlow(false)
    val isFloatingPreviewVisible = _isFloatingPreviewVisible.asStateFlow()

    fun showFloatingPreview(): Boolean {
        if (floatingPreview != null) return true
        if (state != RecorderState.RECORDING) return false
        val useCase = _preview.value ?: return false
        val window = runCatching {
            FloatingVideoPreview(this, useCase, ::hideFloatingPreview)
        }.getOrNull() ?: return false
        if (!window.show()) return false
        floatingPreview = window
        _isFloatingPreviewVisible.value = true
        return true
    }

    fun hideFloatingPreview() {
        floatingPreview?.close()
        floatingPreview = null
        _isFloatingPreviewVisible.value = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingPreview?.onConfigurationChanged()
    }

    // Used to listen and check if the camera is available
    private var _cameraAvailableListener = CompletableDeferred<Unit>()
    private lateinit var _videoFinalizerListener: CompletableDeferred<Unit>;

    // Absolute last completer that can be awaited to ensure that the camera is closed
    private var _cameraCloserListener = CompletableDeferred<Unit>()

    private lateinit var selectedCamera: CameraSelector
    var cameraID: String? = null
        private set
    private var enableAudio by Delegates.notNull<Boolean>()

    var onCameraReady = {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "init") {
            cameraID = intent.getStringExtra("cameraID")
            selectedCamera = CameraInfo.selectorForId(cameraID)
            enableAudio = intent.getBooleanExtra("enableAudio", true)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun start() {
        super.start()

        scope.launch {
            openCamera()
        }
    }

    override suspend fun stop() {
        super.stop()

        stopActiveRecording()

        // Camera can only be closed after the recording has been finalized
        withTimeoutOrNull(CAMERA_CLOSE_TIMEOUT) {
            _videoFinalizerListener.await()
        }

        closeCamera()

        withTimeoutOrNull(CAMERA_CLOSE_TIMEOUT) {
            _cameraCloserListener.await()
        }
    }

    override fun pause() {
        super.pause()

        stopActiveRecording()
    }

    override fun onDestroy() {
        hideFloatingPreview()
        _preview.value?.setSurfaceProvider(null)
        _preview.value = null
        super.onDestroy()
    }

    override fun startForegroundService() {
        ServiceCompat.startForeground(
            this,
            NotificationHelper.RECORDER_CHANNEL_NOTIFICATION_ID,
            getNotificationHelper().buildStartingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (enableAudio)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                else
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                0
            },
        )
    }

    @SuppressLint("MissingPermission")
    override fun startNewCycle() {
        super.startNewCycle()

        fun action() {
            stopActiveRecording()
            val newRecording = prepareVideoRecording()

            _videoFinalizerListener = CompletableDeferred()

            activeRecording = newRecording.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize && (this@VideoRecorderService.state == RecorderState.STOPPED || this@VideoRecorderService.state == RecorderState.PAUSED)) {
                    _videoFinalizerListener.complete(Unit)
                }
            }
        }

        if (_cameraAvailableListener.isCompleted) {
            action()
        } else {
            // Race condition of `startNewCycle` being called before `invokeOnCompletion`
            // has been called can be ignored, as the camera usually opens within 5 seconds
            // and the interval can't be set shorter than 10 seconds.
            _cameraAvailableListener.invokeOnCompletion {
                action()
            }
        }
    }


    // Runs a function in the main thread
    private fun runOnMain(callback: () -> Unit) {
        val mainHandler = ContextCompat.getMainExecutor(this)

        mainHandler.execute(callback)
    }

    private fun buildRecorder() = Recorder.Builder()
        .setQualitySelector(
            settings.videoRecorderSettings.getQualitySelector()
                ?: QualitySelector.from(Quality.HIGHEST)
        )
        .apply {
            if (settings.videoRecorderSettings.targetedVideoBitRate != null) {
                setTargetVideoEncodingBitRate(settings.videoRecorderSettings.targetedVideoBitRate!!)
            }
        }
        .build()

    private fun buildVideoCapture(recorder: Recorder) = VideoCapture.Builder(recorder)
        .apply {
            val frameRate = settings.videoRecorderSettings.targetFrameRate
            if (frameRate != null) {
                setTargetFrameRate(Range(frameRate, frameRate))
            }
        }
        .build()

    // Open the camera.
    // Used to open it for a longer time, shouldn't be called when pausing / resuming.
    // This should only be called when starting a recording.
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private suspend fun openCamera() {
        cameraProvider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(this@VideoRecorderService).get()
        }

        val recorder = buildRecorder()
        videoCapture = buildVideoCapture(recorder)

        runOnMain {
            try {
                // Bind once, before recording starts. Attaching a small window must never
                // unbind or replace VideoCapture while a recording is in progress.
                val preview = Preview.Builder().build()
                camera = try {
                    cameraProvider!!.bindToLifecycle(
                        this, selectedCamera, videoCapture, preview
                    ).also { _preview.value = preview }
                } catch (error: IllegalArgumentException) {
                    // Some camera/quality combinations cannot support both use cases.
                    // Keep background recording available on those devices.
                    cameraProvider!!.unbind(preview)
                    cameraProvider!!.bindToLifecycle(this, selectedCamera, videoCapture)
                }

                cameraID = Camera2CameraInfo.from(camera!!.cameraInfo).cameraId
                onCameraReady()

                _cameraAvailableListener.complete(Unit)
            } catch (error: IllegalArgumentException) {
                onError()
            }
        }
    }

    // Close the camera
    // Used to close it finally, shouldn't be called when pausing / resuming.
    // This should only be called after recording has finished.
    private fun closeCamera() {
        runOnMain {
            hideFloatingPreview()
            _preview.value?.setSurfaceProvider(null)
            _preview.value = null
            runCatching {
                cameraProvider?.unbindAll()
            }
            _cameraCloserListener.complete(Unit)

            // Doesn't need to run on main thread, but
            // if it runs outside `runOnMain`, `cameraProvider` is already null
            // before it's unbound
            cameraProvider = null
            videoCapture = null
            camera = null
        }
    }

    // `resume` override not needed as `startNewCycle` is called by `IntervalRecorderService`

    private fun stopActiveRecording() {
        runCatching {
            activeRecording?.stop()
        }
    }

    private fun getNameForMediaFile() =
        "${batchesFolder.mediaPrefix}$counter.${settings.videoRecorderSettings.fileExtension}"

    @SuppressLint("MissingPermission", "NewApi")
    private fun prepareVideoRecording() =
        videoCapture!!.output
            .let {
                if (batchesFolder.type == BatchesFolder.BatchType.CUSTOM && SUPPORTS_SAVING_VIDEOS_IN_CUSTOM_FOLDERS) {
                    it.prepareRecording(
                        this,
                        FileDescriptorOutputOptions.Builder(
                            batchesFolder.asCustomGetParcelFileDescriptor(
                                counter,
                                settings.videoRecorderSettings.fileExtension
                            )
                        ).build()
                    )
                } else if (batchesFolder.type == BatchesFolder.BatchType.MEDIA) {
                    if (SUPPORTS_SCOPED_STORAGE) {
                        val name = getNameForMediaFile()

                        it.prepareRecording(
                            this,
                            MediaStoreOutputOptions
                                .Builder(
                                    contentResolver,
                                    batchesFolder.scopedMediaContentUri,
                                )
                                .setContentValues(
                                    batchesFolder.asMediaGetScopedStorageContentValues(
                                        name
                                    )
                                )
                                .build()
                        )
                    } else {
                        val name = getNameForMediaFile()

                        it.prepareRecording(
                            this,
                            FileOutputOptions
                                .Builder(batchesFolder.asMediaGetLegacyFile(name))
                                .build()
                        )
                    }
                } else {
                    it.prepareRecording(
                        this,
                        FileOutputOptions.Builder(
                            batchesFolder.asInternalGetFile(
                                counter,
                                settings.videoRecorderSettings.fileExtension
                            ).apply {
                                createNewFile()
                            }
                        ).build()
                    )
                }
            }
            .run {
                if (enableAudio) {
                    return@run withAudioEnabled()
                }

                this
            }

    override fun getRecordingInformation() =
        RecordingInformation(
            folderPath = batchesFolder.exportFolderForSettings(),
            recordingStart = recordingStart,
            maxDuration = settings.maxDuration,
            batchesAmount = batchesFolder.getBatchesForFFmpeg().size,
            fileExtension = settings.videoRecorderSettings.fileExtension,
            intervalDuration = settings.intervalDuration,
            type = RecordingInformation.Type.VIDEO,
        )

    companion object {
        const val CAMERA_CLOSE_TIMEOUT = 20000L
    }

}
