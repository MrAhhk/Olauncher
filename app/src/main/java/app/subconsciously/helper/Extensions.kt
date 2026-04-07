package app.subconsciously.helper

import android.app.AppOpsManager
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import app.subconsciously.BuildConfig
import app.subconsciously.R
import app.subconsciously.data.Constants
import java.util.Calendar

fun View.hideKeyboard() {
    this.clearFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun View.showKeyboard(show: Boolean = true) {
    if (show.not()) return
    if (this.requestFocus())
        postDelayed({
            if (!isAttachedToWindow) return@postDelayed
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
        }, 100)
}


fun Context.resetDefaultLauncher() {
    try {
        val componentName = ComponentName(this, FakeHomeActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        val selector = Intent(Intent.ACTION_MAIN)
        selector.addCategory(Intent.CATEGORY_HOME)
        startActivity(selector)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.isDefaultLauncher(): Boolean {
    val launcherPackageName = getDefaultLauncherPackage(this)
    return BuildConfig.APPLICATION_ID == launcherPackageName
}

fun Context.resetLauncherViaFakeActivity() {
    resetDefaultLauncher()
    if (getDefaultLauncherPackage(this).contains("."))
        startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}

fun Context.openSearch(query: String? = null) {
    val intent = Intent(Intent.ACTION_WEB_SEARCH)
    intent.putExtra(SearchManager.QUERY, query ?: "")
    startActivity(intent)
}

fun Context.isEinkDisplay(): Boolean {
    return try {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.refreshRate <= Constants.MIN_ANIM_REFRESH_RATE
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun Context.searchOnPlayStore(query: String? = null): Boolean {
    return try {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=$query&c=apps")
            ).addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
        )
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/** Device Location master switch (quick settings), not app runtime permission. */
fun Context.isDeviceLocationEnabled(): Boolean {
    val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        lm.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

/**
 * Resolves an implicit intent to a **non-local** activity and starts it explicitly.
 * Avoids [UnsafeImplicitIntentLaunch]: implicit system actions can incorrectly match this app's
 * own non-exported activities after manifest merge / OEM quirks.
 */
private fun Context.startImplicitIntentOnExternalHandler(implicit: Intent): Boolean {
    val candidates: List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                implicit,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(implicit, PackageManager.MATCH_DEFAULT_ONLY)
        }
    val mine = packageName
    val info = candidates.firstOrNull { it.activityInfo.packageName != mine }?.activityInfo
        ?: return false
    val explicit = Intent(implicit).apply {
        component = ComponentName(info.packageName, info.name)
    }
    return try {
        startActivity(explicit)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Opens system UI so the user can turn Location on. Apps cannot enable GPS programmatically;
 * this is the supported follow-up right after granting [ACCESS_FINE_LOCATION].
 */
private fun Context.startLocationSettingsAospPackageFallback() {
    try {
        val scoped = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            setPackage("com.android.settings")
        }
        val ri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                scoped,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(scoped, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val ai = ri?.activityInfo ?: return
        startActivity(
            Intent(scoped).apply {
                component = ComponentName(ai.packageName, ai.name)
            }
        )
    } catch (_: Exception) {}
}

fun Context.openDeviceLocationSettingsOrPanel() {
    if (startImplicitIntentOnExternalHandler(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) return
    startLocationSettingsAospPackageFallback()
}

fun Context.isPackageInstalled(packageName: String, userHandle: UserHandle = android.os.Process.myUserHandle()): Boolean {
    val launcher = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, userHandle)
    return activityInfo.isNotEmpty()
}

@RequiresApi(Build.VERSION_CODES.Q)
fun Context.appUsagePermissionGranted(): Boolean {
    val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOpsManager.unsafeCheckOpNoThrow(
        "android:get_usage_stats",
        android.os.Process.myUid(),
        packageName
    ) == AppOpsManager.MODE_ALLOWED
}

fun Context.formattedTimeSpent(timeSpent: Long): String {
    val seconds = timeSpent / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        timeSpent == 0L -> "0m"

        hours > 0 -> getString(
            R.string.time_spent_hour,
            hours.toString(),
            remainingMinutes.toString()
        )

        minutes > 0 -> {
            getString(R.string.time_spent_min, minutes.toString())
        }

        else -> "<1m"
    }
}

fun Long.convertEpochToMidnight(): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

fun Long.isDaySince(): Int = ((System.currentTimeMillis().convertEpochToMidnight() - this.convertEpochToMidnight())
        / Constants.ONE_DAY_IN_MILLIS).toInt()

fun Long.hasBeenDays(days: Int): Boolean =
    ((System.currentTimeMillis() - this) / Constants.ONE_DAY_IN_MILLIS) >= days

fun Long.hasBeenHours(hours: Int): Boolean =
    ((System.currentTimeMillis() - this) / Constants.ONE_HOUR_IN_MILLIS) >= hours

fun Long.hasBeenMinutes(minutes: Int): Boolean =
    ((System.currentTimeMillis() - this) / Constants.ONE_MINUTE_IN_MILLIS) >= minutes

fun Int.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}
