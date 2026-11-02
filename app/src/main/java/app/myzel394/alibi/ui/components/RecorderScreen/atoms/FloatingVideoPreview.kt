package app.myzel394.alibi.ui.components.RecorderScreen.atoms

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import app.myzel394.alibi.R
import app.myzel394.alibi.ui.utils.FloatingPreviewSize
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A display surface only: the recorder service continues to own the camera. */
class FloatingVideoPreview(
    private val context: Context,
    private val preview: Preview,
    private val onDismiss: () -> Unit,
) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val preferences = context.getSharedPreferences("floating_video_preview", Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density
    private val root = FrameLayout(context)
    private val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FIT_CENTER
        setBackgroundColor(Color.BLACK)
    }
    private val aspectRatio: Float = preview.resolutionInfo?.let { info ->
        if (info.rotationDegrees % 180 == 0)
            info.resolution.width.toFloat() / info.resolution.height
        else info.resolution.height.toFloat() / info.resolution.width
    } ?: (3f / 4f)
    private val params = WindowManager.LayoutParams(
        1, 1,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "Alibi camera preview"
    }
    private var attached = false
    private var cancelGesture: () -> Unit = {}

    init {
        root.background = GradientDrawable().apply {
            setColor(Color.rgb(28, 28, 30))
            cornerRadius = dp(10).toFloat()
        }
        root.clipToOutline = true
        root.elevation = dp(8).toFloat()
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        // A transparent gesture layer leaves the entire window available for the image.
        val gestureLayer = View(context).apply {
            contentDescription = context.getString(R.string.ui_videoRecorder_smallWindow_move) +
                ". " + context.getString(R.string.ui_videoRecorder_smallWindow_resize) +
                ". " + context.getString(R.string.ui_videoRecorder_smallWindow_doubleTapClose)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        root.addView(gestureLayer, FrameLayout.LayoutParams(-1, -1))
        installGestures(gestureLayer)
    }

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        val bounds = screenBounds()
        resize(dp(preferences.getFloat("width_dp", 104f).roundToInt()), update = false)
        params.x = bounds.right - params.width - dp(8)
        params.y = bounds.top + (bounds.height() - params.height) / 2
        clampPosition(bounds)
        return runCatching {
            manager.addView(root, params)
            attached = true
            preview.setSurfaceProvider(previewView.surfaceProvider)
            true
        }.getOrElse {
            close()
            false
        }
    }

    fun close() {
        cancelGesture()
        if (attached) {
            preview.setSurfaceProvider(null)
            runCatching { manager.removeViewImmediate(root) }
            attached = false
        }
    }

    fun onConfigurationChanged() {
        resize(params.width)
    }

    private fun resize(requestedWidth: Int, update: Boolean = true) {
        val bounds = screenBounds()
        val maximum = min(dp(280), min(bounds.width() - dp(16),
            ((bounds.height() - dp(16)) * aspectRatio).toInt())).coerceAtLeast(1)
        params.width = FloatingPreviewSize.width(requestedWidth, dp(72), maximum)
        params.height = FloatingPreviewSize.height(params.width, aspectRatio)
        clampPosition(bounds)
        if (attached && update) updateWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installGestures(view: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var startWidth = 0
        var pressed = false
        var resizing = false
        var dragging = false
        var tapCandidate = false
        var lastTapUpTime = 0L
        var lastTapX = 0f
        var lastTapY = 0f
        val configuration = ViewConfiguration.get(context)
        val touchSlop = configuration.scaledTouchSlop.toFloat()
        val doubleTapSlop = configuration.scaledDoubleTapSlop.toFloat()
        // Scale both dimensions equally so the centered rectangle covers 80% of the area.
        val moveInsetFraction = (1f - sqrt(0.8f)) / 2f
        cancelGesture = {
            pressed = false
            resizing = false
            dragging = false
            tapCandidate = false
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelGesture()
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    startWidth = params.width
                    pressed = true
                    tapCandidate = true
                    // Moving has priority throughout the centered 80% of the window.
                    // The invisible resize target uses only the remaining corner edge.
                    val moveInsetX = view.width * moveInsetFraction
                    val moveInsetY = view.height * moveInsetFraction
                    val inMoveArea = event.x >= moveInsetX && event.x <= view.width - moveInsetX &&
                        event.y >= moveInsetY && event.y <= view.height - moveInsetY
                    val resizeWidth = min(dp(48), view.width / 2)
                    val resizeHeight = min(dp(48), view.height / 2)
                    resizing = !inMoveArea && event.x > view.width - resizeWidth &&
                        event.y > view.height - resizeHeight
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!pressed) return@setOnTouchListener true
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        tapCandidate = false
                        lastTapUpTime = 0L
                        if (!resizing) dragging = true
                    }
                    if (resizing && !tapCandidate) {
                        val delta = if (abs(dx) >= abs(dy * aspectRatio)) dx else dy * aspectRatio
                        resize((startWidth + delta).roundToInt())
                    } else if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        clampPosition(screenBounds())
                        if (attached) updateWindow()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val isTap = pressed && tapCandidate && !dragging &&
                        event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()
                    val tapDx = event.rawX - lastTapX
                    val tapDy = event.rawY - lastTapY
                    val dismiss = isTap && lastTapUpTime != 0L &&
                        event.downTime - lastTapUpTime in 0L..ViewConfiguration.getDoubleTapTimeout().toLong() &&
                        tapDx * tapDx + tapDy * tapDy <= doubleTapSlop * doubleTapSlop
                    lastTapUpTime = if (isTap && !dismiss) event.eventTime else 0L
                    lastTapX = event.rawX
                    lastTapY = event.rawY
                    if (resizing) saveSize()
                    cancelGesture()
                    if (dismiss) onDismiss()
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    lastTapUpTime = 0L
                    if (resizing) saveSize()
                    cancelGesture()
                    true
                }
                else -> true
            }
        }
    }

    private fun saveSize() = preferences.edit().putFloat("width_dp", params.width / density).apply()

    private fun updateWindow() {
        runCatching { manager.updateViewLayout(root, params) }.onFailure { onDismiss() }
    }

    private fun clampPosition(bounds: Rect) {
        params.x = params.x.coerceIn(bounds.left, (bounds.right - params.width).coerceAtLeast(bounds.left))
        params.y = params.y.coerceIn(bounds.top, (bounds.bottom - params.height).coerceAtLeast(bounds.top))
    }

    private fun screenBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Rect(metrics.bounds).apply {
                left += insets.left
                top += insets.top
                right -= insets.right
                bottom -= insets.bottom
            }
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        manager.defaultDisplay.getMetrics(metrics)
        return Rect(0, dp(24), metrics.widthPixels, metrics.heightPixels - dp(24))
    }

    private fun dp(value: Int) = (value * density).roundToInt()
}
