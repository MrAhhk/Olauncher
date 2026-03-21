package app.olauncher.helper

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Blurred / faded "locked" look for rows that cannot be toggled (reflection dialog, hidden-apps list).
 */
fun View.applyLockedBlurEffect(locked: Boolean) {
    if (locked) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alpha = 1f
            setRenderEffect(RenderEffect.createBlurEffect(6f, 6f, Shader.TileMode.CLAMP))
        } else {
            alpha = 0.52f
        }
    } else {
        alpha = 1f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(null)
        }
    }
}
