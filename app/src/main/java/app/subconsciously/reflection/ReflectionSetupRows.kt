package app.subconsciously.reflection

import app.subconsciously.data.DistractionList
import app.subconsciously.data.Prefs

internal object ReflectionSetupRows {

    fun build(
        distractionList: DistractionList,
        prefs: Prefs,
        installedApps: List<Pair<String, String>>,
        hiddenSuffix: String,
    ): MutableList<ReflectionAppRow> {
        val rows = installedApps.map { (label, pkg) ->
            val locked = distractionList.isGameCategory(pkg) || prefs.isPackageHidden(pkg)
            val pauseOn = if (locked) true else distractionList.isDistraction(pkg)
            ReflectionAppRow(
                label = label,
                packageName = pkg,
                checked = pauseOn,
                isLocked = locked,
                reflectionPauseOnAtOpen = pauseOn,
            )
        }.toMutableList()
        ReflectionSetupRowSort.sort(rows, hiddenSuffix)
        return rows
    }
}
