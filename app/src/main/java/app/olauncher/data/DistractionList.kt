package app.olauncher.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.olauncher.R
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale

class DistractionList(private val context: Context) {

    private val prefs = context.getSharedPreferences(
        "app.olauncher", Context.MODE_PRIVATE
    )

    private val appPrefs = Prefs(context)

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
        // User hid this app → reflection pause applies (same as “selected” hidden apps)
        if (appPrefs.isPackageHidden(packageName)) return true
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

    /** True if the installed app is in the GAME category (used for locked reflection rows). */
    fun isGameCategory(packageName: String): Boolean {
        return try {
            val info = context.packageManager
                .getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            info.category == ApplicationInfo.CATEGORY_GAME
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
     * Launchable apps (excluding this launcher), plus any user-hidden apps missing from the
     * scan, sorted by label.
     *
     * We do **not** filter [ApplicationInfo.FLAG_SYSTEM]: many store/updated apps use that flag
     * but still have a launcher. Those apps used to appear only via the hidden-app merge; after
     * unhide they would vanish because [addEligible] skipped them. Inclusion is: not self, has
     * default launch intent.
     */
    fun getAllAppsForReflectionSetup(): List<Pair<String, String>> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        val byPackage = LinkedHashMap<String, String>()

        fun addEligible(app: ApplicationInfo) {
            if (app.packageName == selfPackage) return
            if (pm.getLaunchIntentForPackage(app.packageName) == null) return
            byPackage[app.packageName] = pm.getApplicationLabel(app).toString()
        }

        pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { addEligible(it) }

        // Ensure every user-hidden app appears. Do not skip FLAG_SYSTEM: updated/system apps
        // (Chrome, YouTube, etc.) are often marked system and were incorrectly excluded here and
        // in addEligible(), so hidden apps never showed up in the reflection list.
        val hiddenSuffix = context.getString(R.string.reflection_list_hidden_suffix)
        for (key in HashSet(appPrefs.hiddenApps)) {
            val pkg = if (key.contains("|")) key.substringBefore("|") else key
            if (pkg.isEmpty() || pkg == selfPackage) continue
            if (pkg in byPackage) continue
            try {
                val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_ALL)
                val label = pm.getApplicationLabel(info).toString()
                byPackage[pkg] = label
            } catch (_: Exception) {
                // Stale package id in prefs — ignore
            }
        }

        return byPackage.entries
            .map { (pkg, label) ->
                val displayLabel = if (appPrefs.isPackageHidden(pkg)) {
                    label.removeSuffix(hiddenSuffix).trimEnd() + hiddenSuffix
                } else {
                    label
                }
                Pair(displayLabel, pkg)
            }
            .sortedBy { it.first.lowercase(Locale.getDefault()) }
    }
}
