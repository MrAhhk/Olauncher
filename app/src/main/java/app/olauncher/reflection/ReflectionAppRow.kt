package app.olauncher.reflection

internal data class ReflectionAppRow(
    val label: String,
    val packageName: String,
    var checked: Boolean,
    val isLocked: Boolean,
)
