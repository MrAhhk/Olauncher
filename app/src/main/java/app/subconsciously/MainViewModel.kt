package app.subconsciously

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.subconsciously.data.AppModel
import app.subconsciously.data.BlockManager
import app.subconsciously.data.Constants
import app.subconsciously.data.DistractionList
import app.subconsciously.data.Prefs
import app.subconsciously.helper.SingleLiveEvent
import app.subconsciously.helper.WallpaperWorker
import app.subconsciously.helper.WeatherWorker
import app.subconsciously.helper.formattedTimeSpent
import app.subconsciously.helper.getAppsList
import app.subconsciously.helper.hasBeenMinutes
import app.subconsciously.helper.isOlauncherDefault
import app.subconsciously.helper.isPackageInstalled
import app.subconsciously.helper.showToast
import app.subconsciously.helper.usageStats.EventLogWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class DrawerNavHint(
    val stayOnDrawer: Boolean = false,
    val popToMain: Boolean = false,
    val popOnce: Boolean = false,
    val blockedPackage: String? = null,
    val refreshAppList: Boolean = false,
    val refreshHiddenApps: Boolean = false,
)

private sealed class PauseOutcome {
    data object ProceedLaunch : PauseOutcome()
    data class SilentBlocked(val packageName: String) : PauseOutcome()
    data class ThresholdBlocked(val packageName: String) : PauseOutcome()
    data class Reflection(val model: AppModel) : PauseOutcome()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)

    val firstOpen = MutableLiveData<Boolean>()
    val refreshHome = MutableLiveData<Boolean>()
    val toggleDateTime = MutableLiveData<Unit>()
    val updateSwipeApps = MutableLiveData<Any>()
    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()
    val isOlauncherDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val homeAppAlignment = MutableLiveData<Int>()
    val screenTimeValue = MutableLiveData<String>()

    val showDialog = SingleLiveEvent<String>()
    val checkForMessages = SingleLiveEvent<Unit?>()
    val resetLauncherLiveData = SingleLiveEvent<Unit?>()
    val requestWeatherRefresh = MutableLiveData(false)
    private val blockManager: BlockManager by lazy { BlockManager(getApplication()) }

    val showReflection: SingleLiveEvent<AppModel> = SingleLiveEvent()
    val drawerNavHint: SingleLiveEvent<DrawerNavHint> = SingleLiveEvent()
    /** Fires when a home-screen launch attempt ends blocked (e.g. threshold) so UI can show [BlockedAppSheet]. */
    val showBlockedAfterHomeLaunch: SingleLiveEvent<String> = SingleLiveEvent()
    var pendingApp: AppModel? = null

    private val launcherAppsService by lazy {
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    }

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            if (DistractionList(appContext).isGameCategory(packageName)) {
                getAppList()
            }
        }
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            getAppList()
        }
        override fun onPackageChanged(packageName: String, user: UserHandle) {}
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
    }

    init {
        launcherAppsService.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
        if (prefs.showWeatherWidget) {
            setWeatherWorker()
        }
    }

    override fun onCleared() {
        super.onCleared()
        launcherAppsService.unregisterCallback(packageCallback)
    }

    fun selectedApp(appModel: AppModel, flag: Int, isDrawerLaunchContext: Boolean = false) {
        when (flag) {
            Constants.FLAG_LAUNCH_APP -> {
                viewModelScope.launch {
                    val outcome = withContext(Dispatchers.Default) { evaluatePauseLaunch(appModel) }
                    withContext(Dispatchers.Main.immediate) {
                        applyPauseLaunchOutcome(appModel, flag, outcome, isDrawerLaunchContext)
                    }
                }
            }

            Constants.FLAG_HIDDEN_APPS -> {
                if (appModel !is AppModel.App) return
                viewModelScope.launch {
                    val outcome = withContext(Dispatchers.Default) { evaluatePauseLaunch(appModel) }
                    withContext(Dispatchers.Main.immediate) {
                        applyPauseLaunchOutcome(appModel, flag, outcome, isDrawerLaunchContext)
                    }
                }
            }

            Constants.FLAG_SET_HOME_APP_1 -> saveHomeApp(appModel, 1)
            Constants.FLAG_SET_HOME_APP_2 -> saveHomeApp(appModel, 2)
            Constants.FLAG_SET_HOME_APP_3 -> saveHomeApp(appModel, 3)
            Constants.FLAG_SET_HOME_APP_4 -> saveHomeApp(appModel, 4)
            Constants.FLAG_SET_HOME_APP_5 -> saveHomeApp(appModel, 5)
            Constants.FLAG_SET_HOME_APP_6 -> saveHomeApp(appModel, 6)
            Constants.FLAG_SET_HOME_APP_7 -> saveHomeApp(appModel, 7)
            Constants.FLAG_SET_HOME_APP_8 -> saveHomeApp(appModel, 8)
            Constants.FLAG_SET_HOME_APP_9 -> saveHomeApp(appModel, 9)
            Constants.FLAG_SET_HOME_APP_10 -> saveHomeApp(appModel, 10)
            Constants.FLAG_SET_HOME_APP_11 -> saveHomeApp(appModel, 11)
            Constants.FLAG_SET_HOME_APP_12 -> saveHomeApp(appModel, 12)

            Constants.FLAG_SET_SWIPE_LEFT_APP -> saveSwipeApp(appModel, isLeft = true)
            Constants.FLAG_SET_SWIPE_RIGHT_APP -> saveSwipeApp(appModel, isLeft = false)
            Constants.FLAG_SET_CLOCK_APP -> saveClockApp(appModel)
            Constants.FLAG_SET_CALENDAR_APP -> saveCalendarApp(appModel)
            Constants.FLAG_SET_SCREEN_TIME_APP -> saveScreenTimeApp(appModel)
        }
    }

    /** Runs on a background thread; usage-stats aggregation must not run on main. */
    private fun evaluatePauseLaunch(appModel: AppModel): PauseOutcome {
        val packageName = appModel.appPackage
        val distractionList = DistractionList(appContext)
        if (!distractionList.isDistraction(packageName)) return PauseOutcome.ProceedLaunch

        if (blockManager.isLaunchBlocked(packageName))
            return PauseOutcome.SilentBlocked(packageName)

        blockManager.recordOpen(packageName)
        blockManager.recordThresholdExceededIfNeeded()
        if (blockManager.checkThresholdExceeded(packageName)) {
            blockManager.blockApp(packageName)
            getAppList()
            return PauseOutcome.ThresholdBlocked(packageName)
        }

        logDistractionOpen()
        val finalProb = (getReflectionProbability() *
            blockManager.getThresholdProximityMultiplier()).coerceAtMost(1.0f)
        return if (Random.nextFloat() < finalProb) PauseOutcome.Reflection(appModel)
        else PauseOutcome.ProceedLaunch
    }

    private fun applyPauseLaunchOutcome(
        appModel: AppModel,
        flag: Int,
        outcome: PauseOutcome,
        drawerCtx: Boolean,
    ) {
        when (outcome) {
            PauseOutcome.ProceedLaunch -> {
                pendingApp = null
                when (appModel) {
                    is AppModel.PinnedShortcut -> launchShortcut(appModel)
                    is AppModel.App ->
                        launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
                }
                if (drawerCtx) {
                    drawerNavHint.value = DrawerNavHint(
                        popToMain = flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS,
                        popOnce = flag != Constants.FLAG_LAUNCH_APP && flag != Constants.FLAG_HIDDEN_APPS,
                    )
                }
            }

            is PauseOutcome.SilentBlocked -> {
                pendingApp = null
                if (drawerCtx) {
                    drawerNavHint.value = DrawerNavHint(
                        stayOnDrawer = true,
                        blockedPackage = outcome.packageName,
                        refreshAppList = flag != Constants.FLAG_HIDDEN_APPS,
                        refreshHiddenApps = flag == Constants.FLAG_HIDDEN_APPS,
                    )
                } else {
                    showBlockedAfterHomeLaunch.value = outcome.packageName
                }
            }

            is PauseOutcome.ThresholdBlocked -> {
                pendingApp = null
                if (drawerCtx) {
                    drawerNavHint.value = DrawerNavHint(
                        stayOnDrawer = true,
                        blockedPackage = outcome.packageName,
                        refreshAppList = flag != Constants.FLAG_HIDDEN_APPS,
                        refreshHiddenApps = flag == Constants.FLAG_HIDDEN_APPS,
                    )
                } else {
                    showBlockedAfterHomeLaunch.value = outcome.packageName
                }
            }

            is PauseOutcome.Reflection -> {
                pendingApp = outcome.model
                showReflection.value = outcome.model
                if (drawerCtx) {
                    drawerNavHint.value = DrawerNavHint(stayOnDrawer = true)
                }
            }
        }
    }

    private fun logDistractionOpen() {
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        val updated = prefs.distractionOpensLog.toMutableSet()
        updated.add(now.toString())
        updated.removeAll { it.toLongOrNull() ?: 0L < now - sevenDaysMs }
        prefs.distractionOpensLog = updated
    }

    /**
     * Base reflection chance: tiered decay only while the user has **not** hit the daily distraction
     * threshold on any day in the last 7 days. Any such day → 100% base until it ages out (then tiers apply again).
     */
    private fun getReflectionProbability(): Float {
        if (prefs.hasExceededThresholdInLast7Days()) return 1.0f
        val recent = prefs.distractionOpensLog
            .filter { it.toLongOrNull() ?: 0L > System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L }
        return when (recent.size) {
            in 0..4   -> 1.0f
            in 5..14  -> 0.7f
            in 15..29 -> 0.4f
            else      -> 0.2f
        }
    }

    internal fun launchShortcut(appModel: AppModel.PinnedShortcut) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }
        launcher.getShortcuts(query, appModel.user)?.find { it.id == appModel.shortcutId }
            ?.let { shortcut ->
                launcher.startShortcut(shortcut, null, null)
            }
    }

    private fun saveHomeApp(appModel: AppModel, position: Int) {
        when (appModel) {
            is AppModel.App -> prefs.saveHomeApp(
                location = position,
                name = appModel.appLabel,
                pkg = appModel.appPackage,
                user = appModel.user.toString(),
                activityClassName = appModel.activityClassName,
                isShortcut = false,
                shortcutId = ""
            )
            is AppModel.PinnedShortcut -> prefs.saveHomeApp(
                location = position,
                name = appModel.appLabel,
                pkg = appModel.appPackage,
                user = appModel.user.toString(),
                activityClassName = null,
                isShortcut = true,
                shortcutId = appModel.shortcutId
            )
        }
        refreshHome(false)
        // Return from drawer after selecting a home-slot app.
        drawerNavHint.value = DrawerNavHint(popOnce = true)
    }

    private fun saveSwipeApp(appModel: AppModel, isLeft: Boolean) {
        when (appModel) {
            is AppModel.App -> {
                if (isLeft) {
                    prefs.appNameSwipeLeft = appModel.appLabel
                    prefs.appPackageSwipeLeft = appModel.appPackage
                    prefs.appUserSwipeLeft = appModel.user.toString()
                    prefs.appActivityClassNameSwipeLeft = appModel.activityClassName
                    prefs.isShortcutSwipeLeft = false
                    prefs.shortcutIdSwipeLeft = ""
                } else {
                    prefs.appNameSwipeRight = appModel.appLabel
                    prefs.appPackageSwipeRight = appModel.appPackage
                    prefs.appUserSwipeRight = appModel.user.toString()
                    prefs.appActivityClassNameRight = appModel.activityClassName
                    prefs.isShortcutSwipeRight = false
                    prefs.shortcutIdSwipeRight = ""
                }
            }

            is AppModel.PinnedShortcut -> {
                if (isLeft) {
                    prefs.appNameSwipeLeft = appModel.appLabel
                    prefs.appPackageSwipeLeft = appModel.appPackage
                    prefs.appUserSwipeLeft = appModel.user.toString()
                    prefs.appActivityClassNameSwipeLeft = null
                    prefs.isShortcutSwipeLeft = true
                    prefs.shortcutIdSwipeLeft = appModel.shortcutId
                } else {
                    prefs.appNameSwipeRight = appModel.appLabel
                    prefs.appPackageSwipeRight = appModel.appPackage
                    prefs.appUserSwipeRight = appModel.user.toString()
                    prefs.appActivityClassNameRight = null
                    prefs.isShortcutSwipeRight = true
                    prefs.shortcutIdSwipeRight = appModel.shortcutId
                }
            }
        }
        updateSwipeApps()
    }

    private fun saveClockApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.clockAppPackage = appModel.appPackage
            prefs.clockAppUser = appModel.user.toString()
            prefs.clockAppClassName = appModel.activityClassName
        }
    }

    private fun saveCalendarApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.calendarAppPackage = appModel.appPackage
            prefs.calendarAppUser = appModel.user.toString()
            prefs.calendarAppClassName = appModel.activityClassName
        }
    }

    private fun saveScreenTimeApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.screenTimeAppPackage = appModel.appPackage
            prefs.screenTimeAppUser = appModel.user.toString()
            prefs.screenTimeAppClassName = appModel.activityClassName
        }
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }

    fun refreshHome(appCountUpdated: Boolean) {
        refreshHome.value = appCountUpdated
    }

    fun toggleDateTime() {
        toggleDateTime.postValue(Unit)
    }

    private fun updateSwipeApps() {
        updateSwipeApps.postValue(Unit)
    }

    internal fun launchApp(packageName: String, activityClassName: String?, userHandle: UserHandle) {
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        val isActivityValid = activityClassName.isNullOrBlank().not()
                && activityInfo.any { it.componentName.className == activityClassName }

        val component = if (isActivityValid)
            ComponentName(packageName, activityClassName)
        else {
            when (activityInfo.size) {
                0 -> {
                    appContext.showToast(appContext.getString(R.string.app_not_found))
                    return
                }

                1 -> ComponentName(packageName, activityInfo[0].name)
                else -> ComponentName(packageName, activityInfo[activityInfo.size - 1].name)
            }.also { prefs.updateAppActivityClassName(packageName, it.className) }
        }

        try {
            launcher.startMainActivity(component, userHandle, null, null)
        } catch (e: SecurityException) {
            try {
                launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
            } catch (e: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        } catch (e: Exception) {
            appContext.showToast(appContext.getString(R.string.unable_to_open_app))
        }
    }

    fun getAppList(includeHiddenApps: Boolean = false) {
        viewModelScope.launch {
            val apps = getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps)
            appList.value = apps
        }
    }

    fun getHiddenApps() {
        viewModelScope.launch {
            hiddenApps.value =
                getAppsList(appContext, prefs, includeRegularApps = false, includeHiddenApps = true)
        }
    }

    fun isOlauncherDefault() {
        isOlauncherDefault.value = isOlauncherDefault(appContext)
    }

    fun setWallpaperWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val uploadWorkRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(4, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                Constants.WALLPAPER_WORKER_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                uploadWorkRequest
            )
    }

    fun cancelWallpaperWorker() {
        WorkManager.getInstance(appContext).cancelUniqueWork(Constants.WALLPAPER_WORKER_NAME)
        prefs.dailyWallpaperUrl = ""
        prefs.dailyWallpaper = false
    }

    fun setWeatherWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val weatherWorkRequest = PeriodicWorkRequestBuilder<WeatherWorker>(15, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                Constants.WEATHER_WORKER_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                weatherWorkRequest
            )
    }

    fun cancelWeatherWorker() {
        WorkManager.getInstance(appContext).cancelUniqueWork(Constants.WEATHER_WORKER_NAME)
    }

    fun updateHomeAlignment(gravity: Int) {
        prefs.homeAlignment = gravity
        homeAppAlignment.value = prefs.homeAlignment
    }

    fun getTodaysScreenTime() {
        if (prefs.screenTimeLastUpdated.hasBeenMinutes(1).not()) return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val eventLogWrapper = EventLogWrapper(appContext)
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startTime = calendar.timeInMillis
                val endTime = System.currentTimeMillis()

                val timeSpent = eventLogWrapper.aggregateSimpleUsageStats(
                    eventLogWrapper.aggregateForegroundStats(
                        eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
                    )
                )
                val viewTimeSpent = appContext.formattedTimeSpent(timeSpent)
                screenTimeValue.postValue(viewTimeSpent)
                prefs.screenTimeLastUpdated = endTime
            } catch (e: SecurityException) {
                screenTimeValue.postValue("")
            } catch (e: Exception) {
                screenTimeValue.postValue("")
            }
        }
    }

    fun setDefaultClockApp() {
        viewModelScope.launch {
            try {
                Constants.CLOCK_APP_PACKAGES.firstOrNull { appContext.isPackageInstalled(it) }?.let { packageName ->
                    appContext.packageManager.getLaunchIntentForPackage(packageName)?.component?.className?.let {
                        prefs.clockAppPackage = packageName
                        prefs.clockAppClassName = it
                        prefs.clockAppUser = android.os.Process.myUserHandle().toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}