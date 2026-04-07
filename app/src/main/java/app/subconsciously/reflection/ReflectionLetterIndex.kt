package app.subconsciously.reflection

import java.util.LinkedHashMap
import java.util.Locale

/**
 * First-letter bucketing for reflection setup list sorting and the A–Z / ! / # strip.
 */
internal object ReflectionLetterIndex {

    private const val NON_ALPHA_ORDER = 26

    /** 0–25 = A–Z, 26 = non-alphabet (# bucket). */
    fun normalizedSortKey(label: String, hiddenSuffix: String): Int {
        val clean = label.removeSuffix(hiddenSuffix).trim()
        if (clean.isEmpty()) return NON_ALPHA_ORDER
        val head = clean.take(1).uppercase(Locale.getDefault())
        if (head.isEmpty()) return NON_ALPHA_ORDER
        val c = head[0]
        return if (c in 'A'..'Z') c.code - 'A'.code else NON_ALPHA_ORDER
    }

    fun bucketChar(label: String, hiddenSuffix: String): Char {
        val clean = label.removeSuffix(hiddenSuffix).trim()
        if (clean.isEmpty()) return '#'
        val head = clean.take(1).uppercase(Locale.getDefault())
        if (head.isEmpty()) return '#'
        val c = head[0]
        return if (c in 'A'..'Z') c else '#'
    }

    /** Side index: “!” = reflection pause on; else A–Z / # by first letter of label. */
    fun stripLetter(row: ReflectionAppRow, hiddenSuffix: String): Char {
        if (row.checked) return '!'
        return bucketChar(row.label, hiddenSuffix)
    }

    fun firstIndexPerLetter(rows: List<ReflectionAppRow>, hiddenSuffix: String): Map<Char, Int> {
        val map = LinkedHashMap<Char, Int>()
        for (i in rows.indices) {
            val c = stripLetter(rows[i], hiddenSuffix)
            if (c !in map) map[c] = i
        }
        return map
    }

    val stripLetters: List<Char> = listOf('!') + ('A'..'Z').toList() + '#'
}
