package app.olauncher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class DistractionList(private val context: Context) {

    private val permanentWhitelist = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.camera2",
        "com.google.android.GoogleCamera",
        "com.google.android.apps.maps",
        "com.android.messaging",
        "com.google.android.deskclock",
        "com.android.calendar",
        "com.google.android.calendar"
    )

    private val distractionCategories = setOf(
        ApplicationInfo.CATEGORY_SOCIAL,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_GAME,
        ApplicationInfo.CATEGORY_NEWS
    )

    private val prefs = context.getSharedPreferences("app.olauncher", Context.MODE_PRIVATE)

    fun isDistraction(packageName: String): Boolean {
        if (packageName in permanentWhitelist) return false
        val whitelist = prefs.getStringSet("distraction_whitelist", emptySet()) ?: emptySet()
        if (packageName in whitelist) return false
        val extra = prefs.getStringSet("distraction_extra", emptySet()) ?: emptySet()
        if (packageName in extra) return true
        return try {
            val info = context.packageManager
                .getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            if (info.category == ApplicationInfo.CATEGORY_UNDEFINED) false
            else info.category in distractionCategories
        } catch (e: Exception) {
            false
        }
    }
}
