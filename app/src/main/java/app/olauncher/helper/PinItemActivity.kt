package app.olauncher.helper

import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class PinItemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showToast("Pinning shortcuts requires Android 8+")
            finish()
            return
        }

        try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            val pinItemRequest = launcherApps.getPinItemRequest(intent)
            if (pinItemRequest != null) handleRequestType(pinItemRequest)
            else showToast("Invalid pin request")
        } catch (e: Exception) {
            showToast("Invalid pin request")
        } finally {
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                handleShortcutRequest(pinItemRequest)
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                showToast("Widgets are not supported")
            else -> showToast("Unknown action not supported")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo != null) {
            val success = pinItemRequest.accept()
            showToast(if (success) "Shortcut pinned successfully" else "Failed to pin shortcut")
        } else {
            showToast("Invalid shortcut info")
        }
    }
}
