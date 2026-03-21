package app.olauncher.reflection

import app.olauncher.data.DistractionList
import app.olauncher.data.Prefs

internal object ReflectionSetupRows {

    fun build(
        distractionList: DistractionList,
        prefs: Prefs,
        installedApps: List<Pair<String, String>>,
        hiddenSuffix: String,
    ): MutableList<ReflectionAppRow> {
        val rows = installedApps.map { (label, pkg) ->
            val locked = distractionList.isGameCategory(pkg) || prefs.isPackageHidden(pkg)
            ReflectionAppRow(
                label = label,
                packageName = pkg,
                checked = if (locked) true else distractionList.isDistraction(pkg),
                isLocked = locked,
            )
        }.toMutableList()
        ReflectionSetupRowSort.sort(rows, hiddenSuffix)
        return rows
    }
}
