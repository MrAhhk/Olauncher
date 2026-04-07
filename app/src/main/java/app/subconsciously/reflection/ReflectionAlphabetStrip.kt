package app.subconsciously.reflection

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.subconsciously.R
import app.subconsciously.databinding.DialogReflectionSetupBinding

/**
 * Right-hand A–Z / ! / # strip + Xiaomi-style scrub to scroll the app list.
 */
internal object ReflectionAlphabetStrip {

    fun attach(
        activity: Activity,
        binding: DialogReflectionSetupBinding,
        rows: List<ReflectionAppRow>,
        hiddenSuffix: String,
    ) = attach(activity, binding.reflectionAlphabetIndex, binding.reflectionAppsList, rows, hiddenSuffix)

    fun attach(
        activity: Activity,
        alphabetStrip: LinearLayout,
        recycler: RecyclerView,
        rows: List<ReflectionAppRow>,
        hiddenSuffix: String,
    ) {
        val letterToPosition = ReflectionLetterIndex.firstIndexPerLetter(rows, hiddenSuffix)
        alphabetStrip.removeAllViews()

        val letters = ReflectionLetterIndex.stripLetters
        val lm = recycler.layoutManager as LinearLayoutManager

        letters.forEach { letter ->
            val firstPos = letterToPosition[letter]
            val tv = TextView(activity).apply {
                text = when (letter) {
                    '#' -> "#"
                    '!' -> "!"
                    else -> letter.toString()
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, ReflectionConstants.ALPHABET_STRIP_TEXT_SP)
                setTextColor(activity.getColor(R.color.whiteTrans80))
                gravity = android.view.Gravity.CENTER
                val has = firstPos != null
                alpha = if (has) 1f else ReflectionConstants.INACTIVE_LETTER_ALPHA
                isClickable = false
                isFocusable = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            }
            alphabetStrip.addView(tv)
        }

        var lastSlotIndex = -1
        alphabetStrip.setOnTouchListener { strip, event ->
            val y = event.y.coerceIn(0f, strip.height.toFloat())
            val h = strip.height.toFloat().coerceAtLeast(1f)
            val slot = (y / h * letters.size).toInt().coerceIn(0, letters.size - 1)
            if (slot != lastSlotIndex) {
                lastSlotIndex = slot
                val letter = letters[slot]
                letterToPosition[letter]?.let { pos ->
                    recycler.post { lm.scrollToPositionWithOffset(pos, 0) }
                    strip.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                lastSlotIndex = -1
            }
            true
        }
    }

    fun styleDialogWindow(dialog: AlertDialog, widthFraction: Float) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        val widthPx = (dialog.context.resources.displayMetrics.widthPixels * widthFraction).toInt()
        dialog.window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
