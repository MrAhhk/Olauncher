package app.subconsciously.reflection

import java.util.Locale

internal object ReflectionSetupRowSort {

    fun sort(rows: MutableList<ReflectionAppRow>, hiddenSuffix: String) {
        rows.sortWith(
            compareBy<ReflectionAppRow> { if (it.checked) 0 else 1 }
                .thenBy { if (!it.checked) ReflectionLetterIndex.normalizedSortKey(it.label, hiddenSuffix) else 0 }
                .thenBy { it.label.lowercase(Locale.getDefault()) }
        )
    }
}
