package app.olauncher.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * FrameLayout that feeds every touch event to the [setOnTouchListener] gesture listener
 * BEFORE dispatching to children. This allows swipe/long-press gestures to be recognised
 * across the full screen even when child views (RecyclerView, clock, etc.) would otherwise
 * consume ACTION_DOWN and prevent the normal touch-listener path from firing.
 *
 * When a child view starts scrolling it calls requestDisallowInterceptTouchEvent(true).
 * We use that signal to send a synthetic ACTION_CANCEL to the gesture detector, so that
 * a pending long-press or swipe is cancelled and the RecyclerView scroll doesn't
 * accidentally trigger an app launch.
 *
 * Children still receive and handle their own click/scroll/long-press events normally.
 */
class GestureInterceptFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var gestureListener: OnTouchListener? = null
    private var childScrolling = false

    /** Store the listener ourselves so we can invoke it in [dispatchTouchEvent]. */
    override fun setOnTouchListener(l: OnTouchListener?) {
        gestureListener = l
        // Do NOT forward to super — we call it manually before child dispatch so it
        // fires even when a child consumes the event.
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
        if (disallowIntercept && !childScrolling) {
            childScrolling = true
            // A child (e.g. RecyclerView) has claimed the touch stream for scrolling.
            // Cancel the gesture detector so no long-press or swipe fires mid-scroll.
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            gestureListener?.onTouch(this, cancel)
            cancel.recycle()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) childScrolling = false
        // Always feed to the gesture listener first (for full-screen detection).
        val gestureWantsEvents = gestureListener?.onTouch(this, ev) ?: false
        // Then let normal child dispatch proceed unchanged.
        val childHandled = super.dispatchTouchEvent(ev)
        // Return true if either side is interested so the event stream continues.
        return childHandled || gestureWantsEvents
    }
}
