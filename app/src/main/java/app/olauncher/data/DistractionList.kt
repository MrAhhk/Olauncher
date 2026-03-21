package app.olauncher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class DistractionList(private val context: Context) {

    private val prefs = context.getSharedPreferences(
        "app.olauncher", Context.MODE_PRIVATE
    )

    // Hardcoded popular distraction apps by package name
    private val defaultBlacklist = setOf(
        // Meta family
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.android",
        "com.instagram.barcelona",
        // YouTube family
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids",
        // TikTok family
        "com.zhiliaoapp.musically",
        "com.zhiliaoapp.musically.lite",
        "com.ss.android.ugc.trill",
        // Twitter/X
        "com.twitter.android",
        // Snapchat
        "com.snapchat.android",
        // Reddit
        "com.reddit.frontpage",
        // Pinterest
        "com.pinterest",
        // Streaming
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.hbo.hbomax",
        "tv.twitch.android.app",
        // Short video
        "video.like",
        "com.kwai.video",
        "com.tumblr"
    )

    // Categories that are distraction (GAME; CATEGORY_ENTERTAINMENT is not in public ApplicationInfo API)
    private val distractionCategories = setOf(
        ApplicationInfo.CATEGORY_GAME
    )

    // User-customized lists
    fun getUserWhitelist(): Set<String> =
        prefs.getStringSet("distraction_whitelist", emptySet()) ?: emptySet()

    fun getUserBlacklist(): Set<String> =
        prefs.getStringSet("distraction_extra", emptySet()) ?: emptySet()

    fun addToWhitelist(packageName: String) {
        val white = getUserWhitelist().toMutableSet()
        white.add(packageName)
        val black = getUserBlacklist().toMutableSet()
        black.remove(packageName)
        prefs.edit()
            .putStringSet("distraction_whitelist", white)
            .putStringSet("distraction_extra", black)
            .apply()
    }

    fun addToBlacklist(packageName: String) {
        val black = getUserBlacklist().toMutableSet()
        black.add(packageName)
        val white = getUserWhitelist().toMutableSet()
        white.remove(packageName)
        prefs.edit()
            .putStringSet("distraction_extra", black)
            .putStringSet("distraction_whitelist", white)
            .apply()
    }

    fun isDistraction(packageName: String): Boolean {
        // User explicitly whitelisted → never distraction
        if (packageName in getUserWhitelist()) return false
        // User explicitly blacklisted → always distraction
        if (packageName in getUserBlacklist()) return true
        // Default blacklist
        if (packageName in defaultBlacklist) return true
        // Category check
        return try {
            val info = context.packageManager
                .getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            info.category in distractionCategories
        } catch (e: Exception) {
            false
        }
    }

    /** True if the app would be a distraction from defaults + category only (ignores user lists). */
    private fun isNaturalDistraction(packageName: String): Boolean {
        if (packageName in defaultBlacklist) return true
        return try {
            val info = context.packageManager
                .getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            info.category in distractionCategories
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sync one row from the reflection setup dialog: checked = pause/reflection applies.
     */
    fun applyReflectionSelection(packageName: String, wantPause: Boolean) {
        when {
            wantPause && isNaturalDistraction(packageName) -> {
                val white = getUserWhitelist().toMutableSet()
                white.remove(packageName)
                prefs.edit().putStringSet("distraction_whitelist", white).apply()
            }
            wantPause && !isNaturalDistraction(packageName) -> addToBlacklist(packageName)
            !wantPause -> addToWhitelist(packageName)
        }
    }

    /**
     * Non-system, launchable user apps (excluding this launcher), sorted by label.
     */
    fun getAllAppsForReflectionSetup(): List<Pair<String, String>> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { app ->
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem) return@filter false
                if (app.packageName == selfPackage) return@filter false
                pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .map { app ->
                val label = pm.getApplicationLabel(app).toString()
                Pair(label, app.packageName)
            }
            .sortedBy { it.first }
            .toList()
    }
}
