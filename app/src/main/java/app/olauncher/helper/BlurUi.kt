package app.olauncher.helper

import android.os.Build
import android.view.View

/**
 * Faded "locked" look for rows that are blocked/locked (pinned apps, app drawer, hidden-apps list).
 */
fun View.applyLockedBlurEffect(locked: Boolean) {
    alpha = if (locked) 0.35f else 1f
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setRenderEffect(null)
    }
}
