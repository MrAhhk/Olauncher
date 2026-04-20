package app.subconsciously.data

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashSet
import java.util.Locale

class Prefs(context: Context) {
    companion object {
        private const val MAX_HOME_APPS = 12
    }

    private val PREFS_FILENAME = "app.subconsciously"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_OPEN_TIME = "FIRST_OPEN_TIME"
    private val SHOWN_30_DAY_MESSAGE = "SHOWN_30_DAY_MESSAGE"
    private val SHOWN_90_DAY_MESSAGE = "SHOWN_90_DAY_MESSAGE"
    private val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
    private val FIRST_HIDE = "FIRST_HIDE"
    private val USER_STATE = "USER_STATE"
    private val LOCK_MODE = "LOCK_MODE"
    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val KEYBOARD_MESSAGE = "KEYBOARD_MESSAGE"
    private val DAILY_WALLPAPER = "DAILY_WALLPAPER"
    private val DAILY_WALLPAPER_URL = "DAILY_WALLPAPER_URL"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val HOME_BOTTOM_ALIGNMENT = "HOME_BOTTOM_ALIGNMENT"
    private val APP_LABEL_ALIGNMENT = "APP_LABEL_ALIGNMENT"
    private val STATUS_BAR = "STATUS_BAR"
    private val DATE_TIME_VISIBILITY = "DATE_TIME_VISIBILITY"
    private val SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"
    private val SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"
    private val HIDDEN_APPS = "HIDDEN_APPS"
    private val HIDDEN_APPS_UPDATED = "HIDDEN_APPS_UPDATED"
    private val SHOW_HINT_COUNTER = "SHOW_HINT_COUNTER"
    private val APP_THEME = "APP_THEME"
    private val ABOUT_CLICKED = "ABOUT_CLICKED"
    private val RATE_CLICKED = "RATE_CLICKED"
    private val WALLPAPER_MSG_SHOWN = "WALLPAPER_MSG_SHOWN"
    private val SHARE_SHOWN_TIME = "SHARE_SHOWN_TIME"
    private val SWIPE_DOWN_ACTION = "SWIPE_DOWN_ACTION"
    private val TEXT_SIZE_SCALE = "TEXT_SIZE_SCALE"
    private val HIDE_SET_DEFAULT_LAUNCHER = "HIDE_SET_DEFAULT_LAUNCHER"
    private val SCREEN_TIME_LAST_UPDATED = "SCREEN_TIME_LAST_UPDATED"
    private val LAUNCHER_RESTART_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    private val SHOWN_ON_DAY_OF_YEAR = "SHOWN_ON_DAY_OF_YEAR"
    private val APP_DRAWER_LAST_SHUFFLE_DATE = "APP_DRAWER_LAST_SHUFFLE_DATE"
    private val WEATHER_TEMP_UNIT = "WEATHER_TEMP_UNIT"
    private val SHOW_WEATHER_WIDGET = "SHOW_WEATHER_WIDGET"

    private val APP_NAME_1 = "APP_NAME_1"
    private val APP_NAME_2 = "APP_NAME_2"
    private val APP_NAME_3 = "APP_NAME_3"
    private val APP_NAME_4 = "APP_NAME_4"
    private val APP_NAME_5 = "APP_NAME_5"
    private val APP_NAME_6 = "APP_NAME_6"
    private val APP_NAME_7 = "APP_NAME_7"
    private val APP_NAME_8 = "APP_NAME_8"
    private val APP_PACKAGE_1 = "APP_PACKAGE_1"
    private val APP_PACKAGE_2 = "APP_PACKAGE_2"
    private val APP_PACKAGE_3 = "APP_PACKAGE_3"
    private val APP_PACKAGE_4 = "APP_PACKAGE_4"
    private val APP_PACKAGE_5 = "APP_PACKAGE_5"
    private val APP_PACKAGE_6 = "APP_PACKAGE_6"
    private val APP_PACKAGE_7 = "APP_PACKAGE_7"
    private val APP_PACKAGE_8 = "APP_PACKAGE_8"
    private val APP_ACTIVITY_CLASS_NAME_1 = "APP_ACTIVITY_CLASS_NAME_1"
    private val APP_ACTIVITY_CLASS_NAME_2 = "APP_ACTIVITY_CLASS_NAME_2"
    private val APP_ACTIVITY_CLASS_NAME_3 = "APP_ACTIVITY_CLASS_NAME_3"
    private val APP_ACTIVITY_CLASS_NAME_4 = "APP_ACTIVITY_CLASS_NAME_4"
    private val APP_ACTIVITY_CLASS_NAME_5 = "APP_ACTIVITY_CLASS_NAME_5"
    private val APP_ACTIVITY_CLASS_NAME_6 = "APP_ACTIVITY_CLASS_NAME_6"
    private val APP_ACTIVITY_CLASS_NAME_7 = "APP_ACTIVITY_CLASS_NAME_7"
    private val APP_ACTIVITY_CLASS_NAME_8 = "APP_ACTIVITY_CLASS_NAME_8"
    private val APP_USER_1 = "APP_USER_1"
    private val APP_USER_2 = "APP_USER_2"
    private val APP_USER_3 = "APP_USER_3"
    private val APP_USER_4 = "APP_USER_4"
    private val APP_USER_5 = "APP_USER_5"
    private val APP_USER_6 = "APP_USER_6"
    private val APP_USER_7 = "APP_USER_7"
    private val APP_USER_8 = "APP_USER_8"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT = "APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT = "APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT"
    private val APP_USER_SWIPE_LEFT = "APP_USER_SWIPE_LEFT"
    private val APP_USER_SWIPE_RIGHT = "APP_USER_SWIPE_RIGHT"
    private val CLOCK_APP_PACKAGE = "CLOCK_APP_PACKAGE"
    private val CLOCK_APP_USER = "CLOCK_APP_USER"
    private val CLOCK_APP_CLASS_NAME = "CLOCK_APP_CLASS_NAME"
    private val CALENDAR_APP_PACKAGE = "CALENDAR_APP_PACKAGE"
    private val CALENDAR_APP_USER = "CALENDAR_APP_USER"
    private val CALENDAR_APP_CLASS_NAME = "CALENDAR_APP_CLASS_NAME"
    private val SCREEN_TIME_APP_PACKAGE = "SCREEN_TIME_APP_PACKAGE"
    private val SCREEN_TIME_APP_USER = "SCREEN_TIME_APP_USER"
    private val SCREEN_TIME_APP_CLASS_NAME = "SCREEN_TIME_APP_CLASS_NAME"

    private val IS_SHORTCUT_1 = "IS_SHORTCUT_1"
    private val SHORTCUT_ID_1 = "SHORTCUT_ID_1"
    private val IS_SHORTCUT_2 = "IS_SHORTCUT_2"
    private val SHORTCUT_ID_2 = "SHORTCUT_ID_2"
    private val IS_SHORTCUT_3 = "IS_SHORTCUT_3"
    private val SHORTCUT_ID_3 = "SHORTCUT_ID_3"
    private val IS_SHORTCUT_4 = "IS_SHORTCUT_4"
    private val SHORTCUT_ID_4 = "SHORTCUT_ID_4"
    private val IS_SHORTCUT_5 = "IS_SHORTCUT_5"
    private val SHORTCUT_ID_5 = "SHORTCUT_ID_5"
    private val IS_SHORTCUT_6 = "IS_SHORTCUT_6"
    private val SHORTCUT_ID_6 = "SHORTCUT_ID_6"
    private val IS_SHORTCUT_7 = "IS_SHORTCUT_7"
    private val SHORTCUT_ID_7 = "SHORTCUT_ID_7"
    private val IS_SHORTCUT_8 = "IS_SHORTCUT_8"
    private val SHORTCUT_ID_8 = "SHORTCUT_ID_8"

    private val SHORTCUT_ID_SWIPE_LEFT = "SHORTCUT_ID_SWIPE_LEFT"
    private val IS_SHORTCUT_SWIPE_LEFT = "IS_SHORTCUT_SWIPE_LEFT"
    private val SHORTCUT_ID_SWIPE_RIGHT = "SHORTCUT_ID_SWIPE_RIGHT"
    private val IS_SHORTCUT_SWIPE_RIGHT = "IS_SHORTCUT_SWIPE_RIGHT"

    /** yyyyMMdd days when distraction usage reached the daily threshold (reflection base stays 100% if any in last 7 days). */
    private val EXCEEDED_THRESHOLD_DATES = "exceeded_threshold_dates"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value).apply() }

    var firstOpenTime: Long
        get() = prefs.getLong(FIRST_OPEN_TIME, 0L)
        set(value) = prefs.edit { putLong(FIRST_OPEN_TIME, value).apply() }

    var shown30DayMessage: Boolean
        get() = prefs.getBoolean(SHOWN_30_DAY_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(SHOWN_30_DAY_MESSAGE, value).apply() }

    var shown90DayMessage: Boolean
        get() = prefs.getBoolean(SHOWN_90_DAY_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(SHOWN_90_DAY_MESSAGE, value).apply() }

    var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_SETTINGS_OPEN, value).apply() }

    var firstHide: Boolean
        get() = prefs.getBoolean(FIRST_HIDE, true)
        set(value) = prefs.edit { putBoolean(FIRST_HIDE, value).apply() }

    var userState: String
        get() = prefs.getString(USER_STATE, Constants.UserState.START).toString()
        set(value) = prefs.edit { putString(USER_STATE, value).apply() }

    var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit { putBoolean(LOCK_MODE, value).apply() }

    var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefs.edit { putBoolean(AUTO_SHOW_KEYBOARD, value).apply() }

    var keyboardMessageShown: Boolean
        get() = prefs.getBoolean(KEYBOARD_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(KEYBOARD_MESSAGE, value).apply() }

    var dailyWallpaper: Boolean
        get() = prefs.getBoolean(DAILY_WALLPAPER, false)
        set(value) = prefs.edit { putBoolean(DAILY_WALLPAPER, value).apply() }

    var dailyWallpaperUrl: String
        get() = prefs.getString(DAILY_WALLPAPER_URL, "").toString()
        set(value) = prefs.edit { putString(DAILY_WALLPAPER_URL, value).apply() }

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 4)
        set(value) = prefs.edit { putInt(HOME_APPS_NUM, value).apply() }

    var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(HOME_ALIGNMENT, value).apply() }

    var homeBottomAlignment: Boolean
        get() = prefs.getBoolean(HOME_BOTTOM_ALIGNMENT, false)
        set(value) = prefs.edit { putBoolean(HOME_BOTTOM_ALIGNMENT, value).apply() }

    var appLabelAlignment: Int
        get() = prefs.getInt(APP_LABEL_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(APP_LABEL_ALIGNMENT, value).apply() }

    var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(STATUS_BAR, value).apply() }

    var dateTimeVisibility: Int
        get() = prefs.getInt(DATE_TIME_VISIBILITY, Constants.DateTime.ON)
        set(value) = prefs.edit { putInt(DATE_TIME_VISIBILITY, value).apply() }

    var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_LEFT_ENABLED, value).apply() }

    var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_RIGHT_ENABLED, value).apply() }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(APP_THEME, value).apply() }

    /** Weather temp unit: "system" (by location), "celsius", "fahrenheit" */
    var weatherTempUnit: String
        get() = prefs.getString(WEATHER_TEMP_UNIT, "system").toString()
        set(value) = prefs.edit { putString(WEATHER_TEMP_UNIT, value).apply() }

    var showWeatherWidget: Boolean
        get() = prefs.getBoolean(SHOW_WEATHER_WIDGET, true)
        set(value) = prefs.edit { putBoolean(SHOW_WEATHER_WIDGET, value).apply() }

    var textSizeScale: Float
        get() = prefs.getFloat(TEXT_SIZE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(TEXT_SIZE_SCALE, value).apply() }

    var hideSetDefaultLauncher: Boolean
        get() = prefs.getBoolean(HIDE_SET_DEFAULT_LAUNCHER, false)
        set(value) = prefs.edit { putBoolean(HIDE_SET_DEFAULT_LAUNCHER, value).apply() }

    var screenTimeLastUpdated: Long
        get() = prefs.getLong(SCREEN_TIME_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(SCREEN_TIME_LAST_UPDATED, value).apply() }

    var launcherRestartTimestamp: Long
        get() = prefs.getLong(LAUNCHER_RESTART_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(LAUNCHER_RESTART_TIMESTAMP, value).apply() }

    var shownOnDayOfYear: Int
        get() = prefs.getInt(SHOWN_ON_DAY_OF_YEAR, 0)
        set(value) = prefs.edit { putInt(SHOWN_ON_DAY_OF_YEAR, value).apply() }

    var appDrawerLastShuffleDate: Int
        get() = prefs.getInt(APP_DRAWER_LAST_SHUFFLE_DATE, 0)
        set(value) = prefs.edit { putInt(APP_DRAWER_LAST_SHUFFLE_DATE, value).apply() }

    var hiddenApps: MutableSet<String>
        get() = HashSet(prefs.getStringSet(HIDDEN_APPS, emptySet()) ?: emptySet())
        set(value) = prefs.edit { putStringSet(HIDDEN_APPS, HashSet(value)).apply() }

    /** True if this package is in the user's hidden-apps set (any profile). */
    fun isPackageHidden(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return hiddenApps.any { key ->
            if (key.contains("|")) key.substringBefore("|") == packageName
            else key == packageName
        }
    }

    var hiddenAppsUpdated: Boolean
        get() = prefs.getBoolean(HIDDEN_APPS_UPDATED, false)
        set(value) = prefs.edit { putBoolean(HIDDEN_APPS_UPDATED, value).apply() }

    var toShowHintCounter: Int
        get() = prefs.getInt(SHOW_HINT_COUNTER, 1)
        set(value) = prefs.edit { putInt(SHOW_HINT_COUNTER, value).apply() }

    var aboutClicked: Boolean
        get() = prefs.getBoolean(ABOUT_CLICKED, false)
        set(value) = prefs.edit { putBoolean(ABOUT_CLICKED, value).apply() }

    var rateClicked: Boolean
        get() = prefs.getBoolean(RATE_CLICKED, false)
        set(value) = prefs.edit { putBoolean(RATE_CLICKED, value).apply() }

    var wallpaperMsgShown: Boolean
        get() = prefs.getBoolean(WALLPAPER_MSG_SHOWN, false)
        set(value) = prefs.edit { putBoolean(WALLPAPER_MSG_SHOWN, value).apply() }

    var shareShownTime: Long
        get() = prefs.getLong(SHARE_SHOWN_TIME, 0L)
        set(value) = prefs.edit { putLong(SHARE_SHOWN_TIME, value).apply() }

    var swipeDownAction: Int
        get() = prefs.getInt(SWIPE_DOWN_ACTION, Constants.SwipeDownAction.NOTIFICATIONS)
        set(value) = prefs.edit { putInt(SWIPE_DOWN_ACTION, value).apply() }

    var appName1: String
        get() = prefs.getString(APP_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_1, value).apply() }

    var appName2: String
        get() = prefs.getString(APP_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_2, value).apply() }

    var appName3: String
        get() = prefs.getString(APP_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_3, value).apply() }

    var appName4: String
        get() = prefs.getString(APP_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_4, value).apply() }

    var appName5: String
        get() = prefs.getString(APP_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_5, value).apply() }

    var appName6: String
        get() = prefs.getString(APP_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_6, value).apply() }

    var appName7: String
        get() = prefs.getString(APP_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_7, value).apply() }

    var appName8: String
        get() = prefs.getString(APP_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_8, value).apply() }

    var appPackage1: String
        get() = prefs.getString(APP_PACKAGE_1, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_1, value).apply() }

    var appPackage2: String
        get() = prefs.getString(APP_PACKAGE_2, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_2, value).apply() }

    var appPackage3: String
        get() = prefs.getString(APP_PACKAGE_3, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_3, value).apply() }

    var appPackage4: String
        get() = prefs.getString(APP_PACKAGE_4, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_4, value).apply() }

    var appPackage5: String
        get() = prefs.getString(APP_PACKAGE_5, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_5, value).apply() }

    var appPackage6: String
        get() = prefs.getString(APP_PACKAGE_6, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_6, value).apply() }

    var appPackage7: String
        get() = prefs.getString(APP_PACKAGE_7, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_7, value).apply() }

    var appPackage8: String
        get() = prefs.getString(APP_PACKAGE_8, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_8, value).apply() }

    var appActivityClassName1: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_1, value).apply() }

    var appActivityClassName2: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_2, value).apply() }

    var appActivityClassName3: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_3, value).apply() }

    var appActivityClassName4: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_4, value).apply() }

    var appActivityClassName5: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_5, value).apply() }

    var appActivityClassName6: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_6, value).apply() }

    var appActivityClassName7: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_7, value).apply() }

    var appActivityClassName8: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_8, value).apply() }

    var appUser1: String
        get() = prefs.getString(APP_USER_1, "").toString()
        set(value) = prefs.edit { putString(APP_USER_1, value).apply() }

    var appUser2: String
        get() = prefs.getString(APP_USER_2, "").toString()
        set(value) = prefs.edit { putString(APP_USER_2, value).apply() }

    var appUser3: String
        get() = prefs.getString(APP_USER_3, "").toString()
        set(value) = prefs.edit { putString(APP_USER_3, value).apply() }

    var appUser4: String
        get() = prefs.getString(APP_USER_4, "").toString()
        set(value) = prefs.edit { putString(APP_USER_4, value).apply() }

    var appUser5: String
        get() = prefs.getString(APP_USER_5, "").toString()
        set(value) = prefs.edit { putString(APP_USER_5, value).apply() }

    var appUser6: String
        get() = prefs.getString(APP_USER_6, "").toString()
        set(value) = prefs.edit { putString(APP_USER_6, value).apply() }

    var appUser7: String
        get() = prefs.getString(APP_USER_7, "").toString()
        set(value) = prefs.edit { putString(APP_USER_7, value).apply() }

    var appUser8: String
        get() = prefs.getString(APP_USER_8, "").toString()
        set(value) = prefs.edit { putString(APP_USER_8, value).apply() }

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "Camera").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_LEFT, value).apply() }

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "Phone").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_RIGHT, value).apply() }

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_LEFT, value).apply() }

    var appActivityClassNameSwipeLeft: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, value).apply() }

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_RIGHT, value).apply() }

    var appActivityClassNameRight: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, value).apply() }

    var appUserSwipeLeft: String
        get() = prefs.getString(APP_USER_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_LEFT, value).apply() }

    var appUserSwipeRight: String
        get() = prefs.getString(APP_USER_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_RIGHT, value).apply() }

    var clockAppPackage: String
        get() = prefs.getString(CLOCK_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_PACKAGE, value).apply() }

    var clockAppUser: String
        get() = prefs.getString(CLOCK_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_USER, value).apply() }

    var clockAppClassName: String?
        get() = prefs.getString(CLOCK_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_CLASS_NAME, value).apply() }

    var calendarAppPackage: String
        get() = prefs.getString(CALENDAR_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_PACKAGE, value).apply() }

    var calendarAppUser: String
        get() = prefs.getString(CALENDAR_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_USER, value).apply() }

    var calendarAppClassName: String?
        get() = prefs.getString(CALENDAR_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_CLASS_NAME, value).apply() }

    var screenTimeAppPackage: String
        get() = prefs.getString(SCREEN_TIME_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_PACKAGE, value).apply() }

    var screenTimeAppUser: String
        get() = prefs.getString(SCREEN_TIME_APP_USER, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_USER, value).apply() }

    var screenTimeAppClassName: String?
        get() = prefs.getString(SCREEN_TIME_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_CLASS_NAME, value).apply() }

    var isShortcut1: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_1, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_1, value) }

    var shortcutId1: String
        get() = prefs.getString(SHORTCUT_ID_1, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_1, value) }

    var isShortcut2: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_2, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_2, value) }

    var shortcutId2: String
        get() = prefs.getString(SHORTCUT_ID_2, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_2, value) }

    var isShortcut3: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_3, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_3, value) }

    var shortcutId3: String
        get() = prefs.getString(SHORTCUT_ID_3, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_3, value) }

    var isShortcut4: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_4, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_4, value) }

    var shortcutId4: String
        get() = prefs.getString(SHORTCUT_ID_4, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_4, value) }

    var isShortcut5: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_5, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_5, value) }

    var shortcutId5: String
        get() = prefs.getString(SHORTCUT_ID_5, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_5, value) }

    var isShortcut6: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_6, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_6, value) }

    var shortcutId6: String
        get() = prefs.getString(SHORTCUT_ID_6, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_6, value) }

    var isShortcut7: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_7, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_7, value) }

    var shortcutId7: String
        get() = prefs.getString(SHORTCUT_ID_7, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_7, value) }

    var isShortcut8: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_8, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_8, value) }

    var shortcutId8: String
        get() = prefs.getString(SHORTCUT_ID_8, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_8, value) }

    var shortcutIdSwipeLeft: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_LEFT, value) }

    var isShortcutSwipeLeft: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_LEFT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_LEFT, value) }

    var shortcutIdSwipeRight: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_RIGHT, value) }

    var isShortcutSwipeRight: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_RIGHT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_RIGHT, value) }

    fun getAppName(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_NAME_1, "").toString()
            2 -> prefs.getString(APP_NAME_2, "").toString()
            3 -> prefs.getString(APP_NAME_3, "").toString()
            4 -> prefs.getString(APP_NAME_4, "").toString()
            5 -> prefs.getString(APP_NAME_5, "").toString()
            6 -> prefs.getString(APP_NAME_6, "").toString()
            7 -> prefs.getString(APP_NAME_7, "").toString()
            8 -> prefs.getString(APP_NAME_8, "").toString()
            in 9..MAX_HOME_APPS -> prefs.getString("APP_NAME_$location", "").toString()
            else -> ""
        }
    }

    fun getAppPackage(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_PACKAGE_1, "").toString()
            2 -> prefs.getString(APP_PACKAGE_2, "").toString()
            3 -> prefs.getString(APP_PACKAGE_3, "").toString()
            4 -> prefs.getString(APP_PACKAGE_4, "").toString()
            5 -> prefs.getString(APP_PACKAGE_5, "").toString()
            6 -> prefs.getString(APP_PACKAGE_6, "").toString()
            7 -> prefs.getString(APP_PACKAGE_7, "").toString()
            8 -> prefs.getString(APP_PACKAGE_8, "").toString()
            in 9..MAX_HOME_APPS -> prefs.getString("APP_PACKAGE_$location", "").toString()
            else -> ""
        }
    }

    fun getAppActivityClassName(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_1, "").toString()
            2 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_2, "").toString()
            3 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_3, "").toString()
            4 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_4, "").toString()
            5 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_5, "").toString()
            6 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_6, "").toString()
            7 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_7, "").toString()
            8 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_8, "").toString()
            in 9..MAX_HOME_APPS -> prefs.getString("APP_ACTIVITY_CLASS_NAME_$location", "").toString()
            else -> ""
        }
    }

    fun getAppUser(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_USER_1, "").toString()
            2 -> prefs.getString(APP_USER_2, "").toString()
            3 -> prefs.getString(APP_USER_3, "").toString()
            4 -> prefs.getString(APP_USER_4, "").toString()
            5 -> prefs.getString(APP_USER_5, "").toString()
            6 -> prefs.getString(APP_USER_6, "").toString()
            7 -> prefs.getString(APP_USER_7, "").toString()
            8 -> prefs.getString(APP_USER_8, "").toString()
            in 9..MAX_HOME_APPS -> prefs.getString("APP_USER_$location", "").toString()
            else -> ""
        }
    }

    fun getShortcutId(location: Int): String {
        return when (location) {
            1 -> shortcutId1
            2 -> shortcutId2
            3 -> shortcutId3
            4 -> shortcutId4
            5 -> shortcutId5
            6 -> shortcutId6
            7 -> shortcutId7
            8 -> shortcutId8
            in 9..MAX_HOME_APPS -> prefs.getString("SHORTCUT_ID_$location", "").toString()
            else -> ""
        }
    }

    fun getIsShortcut(location: Int): Boolean {
        return when (location) {
            1 -> isShortcut1
            2 -> isShortcut2
            3 -> isShortcut3
            4 -> isShortcut4
            5 -> isShortcut5
            6 -> isShortcut6
            7 -> isShortcut7
            8 -> isShortcut8
            in 9..MAX_HOME_APPS -> prefs.getBoolean("IS_SHORTCUT_$location", false)
            else -> false
        }
    }

    fun setResolvedAppActivityClassName(location: Int, activityClassName: String) {
        when (location) {
            1 -> appActivityClassName1 = activityClassName
            2 -> appActivityClassName2 = activityClassName
            3 -> appActivityClassName3 = activityClassName
            4 -> appActivityClassName4 = activityClassName
            5 -> appActivityClassName5 = activityClassName
            6 -> appActivityClassName6 = activityClassName
            7 -> appActivityClassName7 = activityClassName
            8 -> appActivityClassName8 = activityClassName
            in 9..MAX_HOME_APPS -> prefs.edit { putString("APP_ACTIVITY_CLASS_NAME_$location", activityClassName) }
        }
    }

    fun updateAppActivityClassName(packageName: String, activityClassName: String) {
        for (i in 1..MAX_HOME_APPS) {
            if (getAppPackage(i) == packageName) setResolvedAppActivityClassName(i, activityClassName)
        }
        if (clockAppPackage == packageName) clockAppClassName = activityClassName
        if (calendarAppPackage == packageName) calendarAppClassName = activityClassName
        if (screenTimeAppPackage == packageName) screenTimeAppClassName = activityClassName
        if (appPackageSwipeLeft == packageName) appActivityClassNameSwipeLeft = activityClassName
        if (appPackageSwipeRight == packageName) appActivityClassNameRight = activityClassName
    }

    fun setAppName(location: Int, value: String) {
        when (location) {
            1 -> appName1 = value
            2 -> appName2 = value
            3 -> appName3 = value
            4 -> appName4 = value
            5 -> appName5 = value
            6 -> appName6 = value
            7 -> appName7 = value
            8 -> appName8 = value
            in 9..MAX_HOME_APPS -> prefs.edit { putString("APP_NAME_$location", value) }
        }
    }

    fun setAppPackage(location: Int, value: String) {
        when (location) {
            1 -> appPackage1 = value
            2 -> appPackage2 = value
            3 -> appPackage3 = value
            4 -> appPackage4 = value
            5 -> appPackage5 = value
            6 -> appPackage6 = value
            7 -> appPackage7 = value
            8 -> appPackage8 = value
            in 9..MAX_HOME_APPS -> prefs.edit { putString("APP_PACKAGE_$location", value) }
        }
    }

    fun setAppUser(location: Int, value: String) {
        when (location) {
            1 -> appUser1 = value
            2 -> appUser2 = value
            3 -> appUser3 = value
            4 -> appUser4 = value
            5 -> appUser5 = value
            6 -> appUser6 = value
            7 -> appUser7 = value
            8 -> appUser8 = value
            in 9..MAX_HOME_APPS -> prefs.edit { putString("APP_USER_$location", value) }
        }
    }

    fun setAppActivityClassName(location: Int, value: String?) {
        when (location) {
            1 -> appActivityClassName1 = value
            2 -> appActivityClassName2 = value
            3 -> appActivityClassName3 = value
            4 -> appActivityClassName4 = value
            5 -> appActivityClassName5 = value
            6 -> appActivityClassName6 = value
            7 -> appActivityClassName7 = value
            8 -> appActivityClassName8 = value
            in 9..MAX_HOME_APPS -> prefs.edit { putString("APP_ACTIVITY_CLASS_NAME_$location", value) }
        }
    }

    fun setIsShortcut(location: Int, value: Boolean) {
        when (location) {
            1 -> isShortcut1 = value
            2 -> isShortcut2 = value
            3 -> isShortcut3 = value
            4 -> isShortcut4 = value
            5 -> isShortcut5 = value
            6 -> isShortcut6 = value
            7 -> isShortcut7 = value
            8 -> isShortcut8 = value
            in 9..MAX_HOME_APPS -> prefs.edit { putBoolean("IS_SHORTCUT_$location", value) }
        }
    }

    fun setShortcutId(location: Int, value: String) {
        when (location) {
            1 -> shortcutId1 = value
            2 -> shortcutId2 = value
            3 -> shortcutId3 = value
            4 -> shortcutId4 = value
            5 -> shortcutId5 = value
            6 -> shortcutId6 = value
            7 -> shortcutId7 = value
            8 -> shortcutId8 = value
            in 9..MAX_HOME_APPS -> prefs.edit { putString("SHORTCUT_ID_$location", value) }
        }
    }

    fun saveHomeApp(
        location: Int,
        name: String,
        pkg: String,
        user: String,
        activityClassName: String?,
        isShortcut: Boolean,
        shortcutId: String
    ) {
        prefs.edit {
            putString("APP_NAME_$location", name)
            putString("APP_PACKAGE_$location", pkg)
            putString("APP_USER_$location", user)
            putString("APP_ACTIVITY_CLASS_NAME_$location", activityClassName)
            putBoolean("IS_SHORTCUT_$location", isShortcut)
            putString("SHORTCUT_ID_$location", shortcutId)
        }
    }

    fun clearHomeApp(location: Int) {
        setAppName(location, "")
        setAppPackage(location, "")
        setAppUser(location, "")
        setAppActivityClassName(location, "")
        setIsShortcut(location, false)
        setShortcutId(location, "")
    }

    fun getAppRenameLabel(appPackage: String): String = prefs.getString(appPackage, "").toString()

    fun setAppRenameLabel(appPackage: String, renameLabel: String) = prefs.edit { putString(appPackage, renameLabel) }

    var pauseCount: Int
        get() = prefs.getInt("pause_count", 0)
        set(value) = prefs.edit { putInt("pause_count", value) }

    var distractionOpensLog: Set<String>
        get() = prefs.getStringSet("distraction_opens_log", emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet("distraction_opens_log", value) }

    /** True if any calendar day in the last 7 days hit the daily distraction threshold. */
    fun hasExceededThresholdInLast7Days(): Boolean {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L
        val dates = prefs.getStringSet(EXCEEDED_THRESHOLD_DATES, emptySet()) ?: emptySet()
        for (d in dates) {
            val t = try {
                sdf.parse(d)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
            if (t >= cutoff) return true
        }
        return false
    }

    fun recordThresholdExceededToday() {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L
        val existing = (prefs.getStringSet(EXCEEDED_THRESHOLD_DATES, emptySet()) ?: emptySet()).toMutableSet()
        existing.add(sdf.format(Date()))
        val pruned = existing.filter { dateStr ->
            val t = try {
                sdf.parse(dateStr)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
            t >= cutoff
        }.toSet()
        prefs.edit { putStringSet(EXCEEDED_THRESHOLD_DATES, pruned) }
    }

    var identityMode: String
        get() = prefs.getString("identity_mode", "normal").toString()
        set(value) = prefs.edit { putString("identity_mode", value) }

    /** Timestamp of the last mode upgrade (used for 3-day downgrade lock). 0 = never upgraded. */
    var modeUpgradeTimestamp: Long
        get() = prefs.getLong("mode_upgrade_timestamp", 0L)
        set(value) = prefs.edit { putLong("mode_upgrade_timestamp", value) }

    /** One-time reflection setup list (games/hidden locked; choose pause apps). */
    var reflectionSetupDone: Boolean
        get() = prefs.getBoolean("reflection_setup_done", false)
        set(value) = prefs.edit { putBoolean("reflection_setup_done", value) }

    var blockedApps: Set<String>
        get() = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet("blocked_apps", value).apply() }

    var lastBlockTimestamp: Long
        get() = prefs.getLong("last_block_timestamp", 0L)
        set(value) = prefs.edit { putLong("last_block_timestamp", value).apply() }

    var blockCount: Int
        get() = prefs.getInt("block_count_today", 0)
        set(value) = prefs.edit { putInt("block_count_today", value).apply() }

    var appOpenCountsToday: Set<String>
        get() = prefs.getStringSet("app_open_counts_today", emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet("app_open_counts_today", value).apply() }

    var appOpenCountsDate: String
        get() = prefs.getString("app_open_counts_date", "").toString()
        set(value) = prefs.edit { putString("app_open_counts_date", value).apply() }

    var resetTimestamp: Long
        get() = prefs.getLong("block_reset_timestamp", 0L)
        set(value) = prefs.edit { putLong("block_reset_timestamp", value).apply() }

    var burstLevel: Int
        get() = prefs.getInt("burst_level", 0)
        set(value) = prefs.edit { putInt("burst_level", value).apply() }

    var burstWindowStartAt: Long
        get() = prefs.getLong("burst_window_start_at", 0L)
        set(value) = prefs.edit { putLong("burst_window_start_at", value).apply() }

    var burstWindowCount: Int
        get() = prefs.getInt("burst_window_count", 0)
        set(value) = prefs.edit { putInt("burst_window_count", value).apply() }

    var lastDistractionOpenAt: Long
        get() = prefs.getLong("last_distraction_open_at", 0L)
        set(value) = prefs.edit { putLong("last_distraction_open_at", value).apply() }

    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit { putBoolean("onboarding_complete", value) }

    var onboardingReason: String
        get() = prefs.getString("onboarding_reason", "").toString()
        set(value) = prefs.edit { putString("onboarding_reason", value) }
}
