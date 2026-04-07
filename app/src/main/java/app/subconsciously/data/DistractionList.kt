package app.subconsciously.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import app.subconsciously.R
import app.subconsciously.reflection.ReflectionAppRow
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale

class DistractionList(private val context: Context) {

    private val prefs = context.getSharedPreferences("app.subconsciously", Context.MODE_PRIVATE)
    private val appPrefs = Prefs(context)

    private var cachedWhitelist: Set<String> = prefs.getStringSet("distraction_whitelist", emptySet()) ?: emptySet()
    private var cachedBlacklist: Set<String> = prefs.getStringSet("distraction_extra", emptySet()) ?: emptySet()

    // Populated by getAllAppsForReflectionSetup() so subsequent calls avoid redundant IPC
    private var infoCache: Map<String, ApplicationInfo> = emptyMap()

    private val defaultBlacklist = setOf(
        // Meta
        "com.facebook.katana", "com.facebook.lite",
        "com.instagram.android", "com.instagram.barcelona",
        // YouTube
        "com.google.android.youtube", "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids",
        // TikTok
        "com.zhiliaoapp.musically", "com.zhiliaoapp.musically.lite",
        "com.ss.android.ugc.trill",
        // Other social / short video
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "video.like", "com.kwai.video", "com.tumblr",
        "com.bereal.ft",
        // Streaming
        "com.netflix.mediaclient", "com.disney.disneyplus",
        "com.hbo.hbomax", "tv.twitch.android.app",
        // Dating
        "com.tinder",
        "com.bumble.app",
        "co.hinge.app",
        "com.okcupid.okcupid",
        "com.badoo.mobile",
        "com.pof.android",
        "com.grindr.android",
        "com.scruff.android",
        "com.perry.jack",
        "com.myyearbook.m",
        "com.zoosk.zoosk",
        "com.coffeemeetsbagel",
        "com.ftw_and_co.happn",
        "net.lovoo.android",
        "com.skout.android",
        "com.tag.hi",
        "com.feeld.datingapp",
        "com.spark.com",
        // Livestream
        "sg.bigo.live",
        "com.talkwithstranger.liveme",
        "com.younow.android",
        "com.hago.android",
        "com.mico",
        "com.yalla.io",
        "com.nonolive.go",
        "com.streamkar.app",
        // AI companion / girlfriend
        "ai.replika.app",
        "ai.character.app",
        "com.anima.android",
        "com.chai.chai",
        "com.eva.ai",
        "com.candyai.android",
        // Virtual social / avatar
        "com.naver.zepeto",
        "com.powerapp.party"
    )

    private fun getInfo(packageName: String): ApplicationInfo? =
        infoCache[packageName] ?: try {
            context.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
        } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    private fun isGameInfo(info: ApplicationInfo): Boolean {
        val legacyFlag = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            info.category == ApplicationInfo.CATEGORY_GAME || legacyFlag
        } else {
            legacyFlag
        }
    }

    fun isDistraction(packageName: String): Boolean {
        if (packageName in cachedWhitelist) return false
        if (packageName in cachedBlacklist) return true
        if (appPrefs.isPackageHidden(packageName)) return true
        if (packageName in defaultBlacklist) return true
        val info = getInfo(packageName) ?: return false
        return isGameInfo(info)
    }

    /** True if the installed app is in the GAME category or has the legacy game flag. */
    fun isGameCategory(packageName: String): Boolean {
        val info = getInfo(packageName) ?: return false
        return isGameInfo(info)
    }

    private fun isNaturalDistraction(packageName: String): Boolean {
        if (packageName in defaultBlacklist) return true
        val info = getInfo(packageName) ?: return false
        return isGameInfo(info)
    }

    fun addToWhitelist(packageName: String) {
        val white = cachedWhitelist.toMutableSet().also { it.add(packageName) }
        val black = cachedBlacklist.toMutableSet().also { it.remove(packageName) }
        cachedWhitelist = white
        cachedBlacklist = black
        prefs.edit {
            putStringSet("distraction_whitelist", white)
            putStringSet("distraction_extra", black)
        }
    }

    fun addToBlacklist(packageName: String) {
        val black = cachedBlacklist.toMutableSet().also { it.add(packageName) }
        val white = cachedWhitelist.toMutableSet().also { it.remove(packageName) }
        cachedBlacklist = black
        cachedWhitelist = white
        prefs.edit {
            putStringSet("distraction_extra", black)
            putStringSet("distraction_whitelist", white)
        }
    }

    /**
     * Batch-apply all user changes from the reflection setup dialog in a single
     * SharedPreferences write. Only processes rows the user actually changed.
     */
    internal fun applyReflectionSelectionBatch(rows: List<ReflectionAppRow>) {
        val white = cachedWhitelist.toMutableSet()
        val black = cachedBlacklist.toMutableSet()
        for (row in rows) {
            if (row.isLocked) continue
            val wantPause = row.checked
            if (wantPause == row.reflectionPauseOnAtOpen) continue
            val natural = isNaturalDistraction(row.packageName)
            when {
                wantPause && natural  -> white.remove(row.packageName)
                wantPause && !natural -> { black.add(row.packageName); white.remove(row.packageName) }
                else                  -> { white.add(row.packageName); black.remove(row.packageName) }
            }
        }
        cachedWhitelist = white
        cachedBlacklist = black
        prefs.edit {
            putStringSet("distraction_whitelist", white)
            putStringSet("distraction_extra", black)
        }
    }

    /**
     * Launchable apps (excluding this launcher), plus any user-hidden apps missing from the
     * scan, sorted by label. Also populates [infoCache] so callers avoid redundant IPC.
     */
    fun getAllAppsForReflectionSetup(): List<Pair<String, String>> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        val byPackage = LinkedHashMap<String, String>()
        val newInfoCache = HashMap<String, ApplicationInfo>()

        fun addEligible(app: ApplicationInfo) {
            if (app.packageName == selfPackage) return
            if (pm.getLaunchIntentForPackage(app.packageName) == null) return
            byPackage[app.packageName] = pm.getApplicationLabel(app).toString()
            newInfoCache[app.packageName] = app
        }

        pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { addEligible(it) }
        infoCache = newInfoCache

        val hiddenSuffix = context.getString(R.string.reflection_list_hidden_suffix)
        for (key in HashSet(appPrefs.hiddenApps)) {
            val pkg = if (key.contains("|")) key.substringBefore("|") else key
            if (pkg.isEmpty() || pkg == selfPackage || pkg in byPackage) continue
            try {
                val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_ALL)
                byPackage[pkg] = pm.getApplicationLabel(info).toString()
            } catch (_: Exception) { }
        }

        return byPackage.entries
            .map { (pkg, label) ->
                val displayLabel = if (appPrefs.isPackageHidden(pkg))
                    label.removeSuffix(hiddenSuffix).trimEnd() + hiddenSuffix
                else label
                Pair(displayLabel, pkg)
            }
            .sortedBy { it.first.lowercase(Locale.getDefault()) }
    }
}
