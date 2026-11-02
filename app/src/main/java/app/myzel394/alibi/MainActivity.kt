package app.myzel394.alibi

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.datastore.dataStore
import app.myzel394.alibi.db.AppSettingsSerializer
import app.myzel394.alibi.ui.AsLockedApp
import app.myzel394.alibi.ui.LockedAppHandlers
import app.myzel394.alibi.ui.Navigation
import app.myzel394.alibi.ui.theme.AlibiTheme

const val SETTINGS_FILE = "settings.json"
val Context.dataStore by dataStore(
    fileName = SETTINGS_FILE,
    serializer = AppSettingsSerializer()
)

class MainActivity : AppCompatActivity(), DisplayManager.DisplayListener {
    private val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }
    private var requestHighRefreshRate = false
    private var originalDisplayModeId = 0
    private var originalRefreshRate = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        originalDisplayModeId = window.attributes.preferredDisplayModeId
        originalRefreshRate = window.attributes.preferredRefreshRate
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AlibiTheme {
                LockedAppHandlers()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.background
                        )
                ) {
                    AsLockedApp {
                        Navigation()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRate = true
        displayManager.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        updateRefreshRatePreference()
    }

    override fun onPause() {
        requestHighRefreshRate = false
        displayManager.unregisterDisplayListener(this)
        restoreRefreshRatePreference()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateRefreshRatePreference()
    }

    override fun onDisplayAdded(displayId: Int) {
        updateRefreshRatePreference()
    }

    override fun onDisplayRemoved(displayId: Int) {
        updateRefreshRatePreference()
    }

    override fun onDisplayChanged(displayId: Int) {
        updateRefreshRatePreference()
    }

    @Suppress("DEPRECATION")
    private fun updateRefreshRatePreference() {
        if (!requestHighRefreshRate) return
        val display = window.decorView.display
            ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val currentMode = display.mode
        // Keep the current resolution to avoid a disruptive display-mode change.
        // Compose follows display vsync; no extra render loop or camera FPS change is needed.
        val fastestMode = display.supportedModes
            .filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
        if (fastestMode == null || fastestMode.refreshRate <= 60.5f) {
            restoreRefreshRatePreference()
            return
        }
        val attributes = window.attributes
        // Display changes can trigger this callback again. Only update changed values.
        if (attributes.preferredDisplayModeId != fastestMode.modeId ||
            attributes.preferredRefreshRate != fastestMode.refreshRate
        ) {
            attributes.preferredDisplayModeId = fastestMode.modeId
            attributes.preferredRefreshRate = fastestMode.refreshRate
            window.attributes = attributes
        }
    }

    private fun restoreRefreshRatePreference() {
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId != originalDisplayModeId ||
            attributes.preferredRefreshRate != originalRefreshRate
        ) {
            attributes.preferredDisplayModeId = originalDisplayModeId
            attributes.preferredRefreshRate = originalRefreshRate
            window.attributes = attributes
        }
    }
}
