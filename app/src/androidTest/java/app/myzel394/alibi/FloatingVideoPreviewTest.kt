package app.myzel394.alibi

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingVideoPreviewTest {
    @Test
    fun applicationDeclaresCustomWindowPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
    }
}
