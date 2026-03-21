package app.olauncher.reflection

internal data class ReflectionAppRow(
    val label: String,
    val packageName: String,
    var checked: Boolean,
    val isLocked: Boolean,
    /**
     * Snapshot of [checked] when this dialog was opened. Used so the 6s untick pause only runs
     * when turning off reflection on an app that was already paused (saved) — not after tick→untick
     * in the same session.
     */
    val reflectionPauseOnAtOpen: Boolean,
)
