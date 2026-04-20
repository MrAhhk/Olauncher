package app.subconsciously.data

import android.content.Context
import android.util.Log

/**
 * Burst-based adaptive delay.
 *
 * - Consecutive opens within BURST_WINDOW_MS bump a running count.
 * - Hitting BURST_OPENS_TO_LEVEL_UP inside the window bumps burstLevel (max 2).
 * - 6h without any distraction open → full reset to level 0.
 */
class BlockManager(private val context: Context) {

    private val prefs = Prefs(context)

    companion object {
        private const val BURST_WINDOW_MS = 60 * 60 * 1000L
        private const val BURST_OPENS_TO_LEVEL_UP = 3
        private const val FULL_RESET_MS = 6 * 60 * 60 * 1000L
        private const val MAX_BURST_LEVEL = 2

        private const val DELAY_BASE_MS = 3_000L
        private const val DELAY_BURST_MS = 5_500L
        private const val DELAY_EXTREME_MS = 8_000L
    }

    private fun applyResetIfStale() {
        val last = prefs.lastDistractionOpenAt
        if (last == 0L) return
        if (System.currentTimeMillis() - last >= FULL_RESET_MS) {
            prefs.burstLevel = 0
            prefs.burstWindowStartAt = 0L
            prefs.burstWindowCount = 0
        }
    }

    fun isLaunchBlocked(packageName: String): Boolean = false

    fun showsBlockedStyle(packageName: String): Boolean = false

    fun recordOpen(packageName: String) {
        val isDistraction = DistractionList(context).isDistraction(packageName)
        Log.d("BURST", "recordOpen pkg=$packageName isDistraction=$isDistraction")
        if (!isDistraction) return
        applyResetIfStale()
        val now = System.currentTimeMillis()
        val windowStart = prefs.burstWindowStartAt
        val windowAge = if (windowStart == 0L) -1L else now - windowStart
        Log.d("BURST", "  before → level=${prefs.burstLevel} count=${prefs.burstWindowCount} windowAgeMs=$windowAge")
        if (windowStart == 0L || now - windowStart > BURST_WINDOW_MS) {
            prefs.burstWindowStartAt = now
            prefs.burstWindowCount = 1
            Log.d("BURST", "  new window started, count=1")
        } else {
            prefs.burstWindowCount += 1
            val newCount = prefs.burstWindowCount
            Log.d("BURST", "  in window, count=$newCount")
            if (newCount >= BURST_OPENS_TO_LEVEL_UP && prefs.burstLevel < MAX_BURST_LEVEL) {
                prefs.burstLevel += 1
                prefs.burstWindowStartAt = now
                prefs.burstWindowCount = 0
                Log.d("BURST", "  LEVEL UP → level=${prefs.burstLevel}")
            }
        }
        prefs.lastDistractionOpenAt = now
        Log.d("BURST", "  after  → level=${prefs.burstLevel} count=${prefs.burstWindowCount}")
    }

    fun checkThresholdExceeded(packageName: String): Boolean = false

    fun hasExceededDailyThreshold(): Boolean = false

    fun recordThresholdExceededIfNeeded() {}

    fun blockApp(packageName: String) {}

    /** Adaptive overlay delay in ms — varies by mode × burst level. */
    fun getCurrentDelayMs(): Long {
        applyResetIfStale()
        val delay = when (prefs.identityMode) {
            "easy" -> 3_000L  // flat, no escalation
            "hard" -> when (prefs.burstLevel) {
                0 -> 6_000L
                1 -> 12_000L
                else -> 18_000L
            }
            else -> when (prefs.burstLevel) {  // "normal" (default)
                0 -> DELAY_BASE_MS
                1 -> DELAY_BURST_MS
                else -> DELAY_EXTREME_MS
            }
        }
        Log.d("BURST", "getCurrentDelayMs mode=${prefs.identityMode} level=${prefs.burstLevel} → ${delay}ms")
        return delay
    }

    /** Scales reflection probability based on burst level (1.0 → 3.0). */
    fun getThresholdProximityMultiplier(): Float {
        applyResetIfStale()
        return when (prefs.burstLevel) {
            0 -> 1.0f
            1 -> 2.0f
            else -> 3.0f
        }
    }

    fun getBurstLevel(): Int {
        applyResetIfStale()
        return prefs.burstLevel
    }
}
