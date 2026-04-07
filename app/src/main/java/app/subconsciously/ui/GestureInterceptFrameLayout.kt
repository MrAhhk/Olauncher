package app.subconsciously.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * FrameLayout that feeds every touch event to the [setOnTouchListener] gesture listener
 * BEFORE dispatching to children, enabling full-screen swipe/long-press recognition.
 *
 * [childHandledLastDown] — true when the most recent ACTION_DOWN was consumed by a child
 * (e.g. a pinned-app item). Callers can read this to decide whether a detected long-press
 * should open settings or yield to the child's own long-click handler.
 *
 * [childScrolling] — set when a child calls requestDisallowInterceptTouchEvent(true),
 * meaning it has claimed the stream for scrolling. A synthetic ACTION_CANCEL is sent to
 * the gesture detector so no long-press or swipe fires mid-scroll.
 */
class GestureInterceptFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var gestureListener: OnTouchListener? = null
    private var childScrolling = false

    /** True when the last ACTION_DOWN landed on a child that consumed it. */
    var childHandledLastDown = false
        private set

    override fun setOnTouchListener(l: OnTouchListener?) {
        gestureListener = l
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
        if (disallowIntercept && !childScrolling) {
            childScrolling = true
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            gestureListener?.onTouch(this, cancel)
            cancel.recycle()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            childScrolling = false
            childHandledLastDown = false
        }
        val gestureWantsEvents = gestureListener?.onTouch(this, ev) ?: false
        val childHandled = super.dispatchTouchEvent(ev)
        // Record after dispatch so the gesture listener already saw ACTION_DOWN above
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            childHandledLastDown = childHandled
        }
        return childHandled || gestureWantsEvents
    }
}
