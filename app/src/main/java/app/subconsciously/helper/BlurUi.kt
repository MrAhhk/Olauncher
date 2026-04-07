package app.subconsciously.helper

import android.view.View

/**
 * Faded "locked" look for rows that are blocked/locked (pinned apps, app drawer, hidden-apps list).
 */
fun View.applyLockedBlurEffect(locked: Boolean) {
    alpha = if (locked) 0.35f else 1f
}
