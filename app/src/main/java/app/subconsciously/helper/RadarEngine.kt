package app.subconsciously.helper

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import app.subconsciously.data.DistractionList
import app.subconsciously.data.Prefs
import app.subconsciously.data.RadarSnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RadarEngine {

    private const val DAILY_TARGET_S = 3600f  // 1 hour baseline — 15min was too easy to max
    private const val MIN_EVENTS = 10          // more data before trusting the ratios
    private const val DEFAULT = 0.5f

    /** Today only — raw ratios. Returns null when 1–9 opens (not enough data yet). */
    fun buildTodaySnapshot(context: Context, prefs: Prefs): RadarSnapshot? {
        val dateKey = todayKey()
        val opens = prefs.getRadarOpens(dateKey)
        val stops = prefs.getRadarStops(dateKey)
        val hesitations = prefs.getRadarHesitations(dateKey)
        val intents = prefs.getRadarIntent(dateKey)

        // Zero opens = perfectly clean day — full score on all behavioral axes
        if (opens == 0) return RadarSnapshot(1f, 1f, 1f, 1f, 1f)

        if (opens < MIN_EVENTS) return null

        val n = opens.toFloat()
        val rawA = (hesitations / n).coerceIn(0f, 1f)
        val rawC = (stops / n).coerceIn(0f, 1f)
        // Intention = of those who engaged (hesitated), how many actually stopped?
        val rawI = (intents.toFloat() / hesitations.coerceAtLeast(1)).coerceIn(0f, 1f)

        val savedS = savedSecondsFromUsageStats(context, prefs, dateKey)
        val rawT = (savedS / DAILY_TARGET_S).coerceIn(0f, 1f)

        val lateOpens = prefs.getRadarLateOpens(dateKey)
        val lateRate = (lateOpens / n).coerceIn(0f, 1f)
        val streak = streakFactor(prefs, dateKey)
        val rawE = (0.5f * rawC + 0.3f * (1f - lateRate) + 0.2f * streak).coerceIn(0f, 1f)

        return RadarSnapshot(rawA, rawC, rawI, rawT, rawE)
    }

    /**
     * Period snapshot — accumulates raw totals across [startDayAgo..endDayAgo],
     * computes ratios once from the totals. Returns null if insufficient data.
     */
    fun buildPeriodSnapshot(prefs: Prefs, startDayAgo: Int, endDayAgo: Int): RadarSnapshot? {
        var totalOpens = 0
        var totalStops = 0
        var totalHesitations = 0
        var totalIntents = 0
        var totalLate = 0

        for (d in startDayAgo..endDayAgo) {
            val dk = daysAgoKey(d)
            val o = prefs.getRadarOpens(dk)
            if (o == 0) {
                // Clean day: no distraction opens at all — counts as a perfect behavioral day
                totalOpens += 1; totalHesitations += 1; totalStops += 1; totalIntents += 1
            } else {
                totalOpens += o
                totalStops += prefs.getRadarStops(dk)
                totalHesitations += prefs.getRadarHesitations(dk)
                totalIntents += prefs.getRadarIntent(dk)
                totalLate += prefs.getRadarLateOpens(dk)
            }
        }

        if (totalOpens < MIN_EVENTS) return null

        val n = totalOpens.toFloat()
        val rawA = (totalHesitations / n).coerceIn(0f, 1f)
        val rawC = (totalStops / n).coerceIn(0f, 1f)
        val rawI = (totalIntents.toFloat() / totalHesitations.coerceAtLeast(1)).coerceIn(0f, 1f)
        val rawT = DEFAULT  // no reliable historical UsageStats per period
        val lateRate = (totalLate / n).coerceIn(0f, 1f)
        val rawE = (0.5f * rawC + 0.5f * (1f - lateRate)).coerceIn(0f, 1f)

        return RadarSnapshot(rawA, rawC, rawI, rawT, rawE)
    }


    private fun savedSecondsFromUsageStats(context: Context, prefs: Prefs, dateKey: String): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return 0f
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0f
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val date = try { sdf.parse(dateKey) ?: return 0f } catch (_: Exception) { return 0f }
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startToday = cal.timeInMillis
        val endToday = startToday + 24L * 60 * 60 * 1000

        val distractionPkgs = DistractionList(context).getActiveDistractionPackages(prefs)

        val todayMs = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startToday, endToday)
            ?.filter { it.packageName in distractionPkgs }
            ?.sumOf { it.totalTimeInForeground } ?: 0L

        val baselineMs = (1..7).map { d ->
            val s = startToday - d * 24L * 60 * 60 * 1000
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, s, s + 24L * 60 * 60 * 1000)
                ?.filter { it.packageName in distractionPkgs }
                ?.sumOf { it.totalTimeInForeground } ?: 0L
        }.average().toLong()

        if (baselineMs == 0L) return DAILY_TARGET_S * DEFAULT  // no history yet — neutral
        return ((baselineMs - todayMs).coerceAtLeast(0L) / 1000f)
    }

    private fun streakFactor(prefs: Prefs, dateKey: String): Float {
        var streak = 0
        for (d in 1..7) {
            val dk = daysAgo(dateKey, d)
            val s = prefs.getRadarStops(dk)
            val o = prefs.getRadarOpens(dk)
            // o == 0: no distraction apps opened that day — counts as a good day
            if (o == 0 || (s > 0 && s * 2 >= o)) streak++ else break
        }
        return (streak / 7f).coerceIn(0f, 1f)
    }

    fun todayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    fun daysAgoKey(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -days)
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)
    }

    fun daysAgo(dateKey: String, d: Int): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = try { sdf.parse(dateKey) ?: Date() } catch (_: Exception) { Date() }
        cal.add(Calendar.DATE, -d)
        return sdf.format(cal.time)
    }
}
