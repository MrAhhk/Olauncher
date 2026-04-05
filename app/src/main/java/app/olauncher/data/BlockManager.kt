package app.olauncher.data

import android.content.Context
import android.os.Build
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.usageStats.EventLogWrapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BlockManager(private val context: Context) {

    private val prefs = Prefs(context)

    companion object {
        const val TIME_THRESHOLD_MS = 3_600_000L
        const val OPEN_THRESHOLD = 8
        const val BLOCK_DURATION_MS = 24 * 60 * 60 * 1000L
    }

    private var cachedDistractionTimeMs: Long = 0L
    private var cachedDistractionTimeAt: Long = 0L

    private fun checkAndResetIfNeeded() {
        val lastBlock = prefs.lastBlockTimestamp
        if (lastBlock == 0L) return
        if (System.currentTimeMillis() > lastBlock + BLOCK_DURATION_MS) {
            prefs.blockedApps = emptySet()
            prefs.appOpenCountsToday = emptySet()
            prefs.appOpenCountsDate = ""
            prefs.lastBlockTimestamp = 0L
            prefs.blockCount = 0
            prefs.resetTimestamp = System.currentTimeMillis()
            cachedDistractionTimeMs = 0L
            cachedDistractionTimeAt = 0L
        }
    }

    private fun ensureOpenCountDate() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        if (prefs.appOpenCountsDate != today) {
            prefs.appOpenCountsToday = emptySet()
            prefs.appOpenCountsDate = today
        }
    }

    /** True only for apps explicitly in the block set (drawer blur / home pin styling). */
    fun showsBlockedStyle(packageName: String): Boolean {
        checkAndResetIfNeeded()
        return packageName in prefs.blockedApps
    }

    /** True if opening this app should be prevented (explicit block or cascade level 3+). */
    fun isLaunchBlocked(packageName: String): Boolean {
        checkAndResetIfNeeded()
        if (packageName in prefs.blockedApps) return true
        if (prefs.blockCount >= 3) return DistractionList(context).isDistraction(packageName)
        return false
    }

    fun recordOpen(packageName: String) {
        checkAndResetIfNeeded()
        ensureOpenCountDate()
        if (!DistractionList(context).isDistraction(packageName)) return

        val counts = prefs.appOpenCountsToday.toMutableSet()
        val existing = counts.find { it.startsWith("$packageName:") }
        val newCount = if (existing != null) {
            counts.remove(existing)
            existing.substringAfter(":").toIntOrNull()?.plus(1) ?: 1
        } else {
            1
        }
        counts.add("$packageName:$newCount")
        prefs.appOpenCountsToday = counts
    }

    fun checkThresholdExceeded(packageName: String): Boolean {
        checkAndResetIfNeeded()
        val distractionList = DistractionList(context)
        if (!distractionList.isDistraction(packageName)) return false
        if (isLaunchBlocked(packageName)) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context.appUsagePermissionGranted()) {
            getDistractionTimeToday() >= getCurrentThresholdMs()
        } else {
            ensureOpenCountDate()
            getTotalDistractionOpensToday() >= getCurrentThresholdCount()
        }
    }

    fun blockApp(packageName: String) {
        val blocked = prefs.blockedApps.toMutableSet()
        blocked.add(packageName)
        prefs.blockedApps = blocked
        prefs.lastBlockTimestamp = System.currentTimeMillis()
        prefs.resetTimestamp = System.currentTimeMillis()
        cachedDistractionTimeAt = 0L
        prefs.blockCount = prefs.blockCount + 1
        prefs.appOpenCountsToday = emptySet()
    }

    private fun getCurrentThresholdMs(): Long {
        return when (prefs.blockCount) {
            0 -> TIME_THRESHOLD_MS
            1 -> TIME_THRESHOLD_MS / 2
            2 -> TIME_THRESHOLD_MS / 4
            else -> 0L
        }
    }

    private fun getCurrentThresholdCount(): Int {
        return when (prefs.blockCount) {
            0 -> OPEN_THRESHOLD
            1 -> OPEN_THRESHOLD / 2
            2 -> OPEN_THRESHOLD / 4
            else -> 0
        }
    }

    fun getTotalDistractionOpensToday(): Int {
        ensureOpenCountDate()
        return prefs.appOpenCountsToday.sumOf { it.substringAfter(":").toIntOrNull() ?: 0 }
    }

    fun getDistractionTimeToday(): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !context.appUsagePermissionGranted()) return 0L

        val now = System.currentTimeMillis()
        if (now - cachedDistractionTimeAt < 60_000L) return cachedDistractionTimeMs

        return try {
            val eventLogWrapper = EventLogWrapper(context)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = if (prefs.resetTimestamp > 0L) prefs.resetTimestamp else calendar.timeInMillis
            val stats = eventLogWrapper.aggregateForegroundStats(
                eventLogWrapper.getForegroundStatsByTimestamps(
                    startTime,
                    now
                )
            )
            val distractionList = DistractionList(context)
            val total = eventLogWrapper.aggregateSimpleUsageStats(
                stats.filter { distractionList.isDistraction(it.applicationId) }
            )
            cachedDistractionTimeMs = total
            cachedDistractionTimeAt = now
            total
        } catch (_: Exception) {
            0L
        }
    }

    fun getThresholdProximityMultiplier(): Float {
        val ratio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context.appUsagePermissionGranted()) {
            val time = getDistractionTimeToday().toFloat()
            val threshold = getCurrentThresholdMs().toFloat()
            if (threshold <= 0f) return 3.0f
            time / threshold
        } else {
            val count = getTotalDistractionOpensToday().toFloat()
            val threshold = getCurrentThresholdCount().toFloat()
            if (threshold <= 0f) return 3.0f
            count / threshold
        }
        return Math.exp((ratio * ratio * 4.0).toDouble()).toFloat().coerceIn(1.0f, 3.0f)
    }
}
