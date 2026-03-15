package app.olauncher.ui

import android.app.SearchManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getChangedAppTheme
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.openAlarmApp
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.openSearch
import app.olauncher.helper.setPlainWallpaperByTheme
import app.olauncher.helper.showToast
import app.olauncher.listener.OnSwipeTouchListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    companion object {
        private const val MAX_HOME_APPS = 12
        private const val PRIMARY_HOME_APPS = 7
        private const val WEATHER_REFRESH_MS = 30 * 60 * 1000L
        private const val DEFAULT_LAT = 40.7128
        private const val DEFAULT_LON = -74.0060
        private const val PREF_WEATHER = "weather_cache"
        private const val KEY_TEMP = "temp"
        private const val KEY_HUMIDITY = "humidity"
        private const val KEY_VISIBILITY = "visibility_m"
        private const val KEY_AQI = "aqi"
        private const val KEY_CODE = "weather_code"
        private const val KEY_TS = "cache_ts"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
    }

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var pinnedAppsAdapter: PinnedAppsAdapter
    private var batteryReceiver: BroadcastReceiver? = null

    private var pinnedEdgeTextColor = 0
    private var pinnedNormalTextColor = 0
    private var lastEdgeFirstVisible = -1
    private var lastEdgeLastVisible = -1
    private var lastEdgeCanScrollUp = false
    private var lastEdgeCanScrollDown = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initPinnedAppsRecycler()
        initSwipeTouchListener()
        initClickListeners()
        binding.weatherWidget.post { applyWeatherWidgetOffsets() }
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen(false)
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
        registerBatteryReceiver()
        syncBatteryBarWidth()
        refreshWeatherIfNeeded()
        renderCachedWeather()
    }

    override fun onPause() {
        super.onPause()
        unregisterBatteryReceiver()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.phoneCornerAction -> openDialerApp(requireContext())
            R.id.cameraCornerAction -> openCameraApp(requireContext())
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    if (prefs.getAppName(appLocation).isBlank()) openHomeAppSelector(appLocation)
                    else homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isOlauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }

            else -> {
                val location = view.tag?.toString()?.toIntOrNull()
                if (location != null) {
                    openHomeAppSelector(location)
                    return true
                }
            }
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                if (prefs.dailyWallpaper) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            if (binding.firstRunTips.visibility == View.VISIBLE) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
//            if (it) binding.setDefaultLauncher.visibility = View.GONE
//            else binding.setDefaultLauncher.visibility = View.VISIBLE
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.phoneCornerAction.setOnClickListener(this)
        binding.cameraCornerAction.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
        binding.tvScreenTime.setOnLongClickListener(this)
        binding.batteryProgress.setOnClickListener { openBatterySettings() }
        binding.weatherWidget.setOnClickListener { openWeatherPage() }
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = Gravity.CENTER_HORIZONTAL or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

        val dateText = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date())
        binding.date.text = dateText.replace(".,", ",")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val marginTop = if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        }
        binding.tvScreenTime.layoutParams = params
        binding.tvScreenTime.setPadding(10.dpToPx())
    }

    private data class HomeAppInfo(
        val appName: String,
        val packageName: String,
        val activityClassName: String,
        val userString: String,
        val isShortcut: Boolean,
        val shortcutId: String
    )

    private data class HomePinnedItem(
        val location: Int,
        val title: String
    )

    private inner class PinnedAppsAdapter : RecyclerView.Adapter<PinnedAppsAdapter.PinnedAppViewHolder>() {
        private val items = mutableListOf<HomePinnedItem>()

        inner class PinnedAppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.pinnedAppName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinnedAppViewHolder {
            val view = layoutInflater.inflate(R.layout.item_home_pinned_app, parent, false)
            return PinnedAppViewHolder(view)
        }

        override fun onBindViewHolder(holder: PinnedAppViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.title.setTextColor(requireContext().getColor(android.R.color.white))
            holder.itemView.tag = item.location
            holder.itemView.setOnClickListener(this@HomeFragment)
            holder.itemView.setOnLongClickListener(this@HomeFragment)
        }

        override fun getItemCount(): Int = items.size

        fun submitList(newItems: List<HomePinnedItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
            this@HomeFragment.lastEdgeFirstVisible = -1
            this@HomeFragment.lastEdgeLastVisible = -1
            binding.pinnedAppsRecyclerView.post { updateFadeOverlays() }
        }
    }

    private fun initPinnedAppsRecycler() {
        pinnedAppsAdapter = PinnedAppsAdapter()
        binding.pinnedAppsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.pinnedAppsRecyclerView.setHasFixedSize(true)
        binding.pinnedAppsRecyclerView.adapter = pinnedAppsAdapter
        LinearSnapHelper().attachToRecyclerView(binding.pinnedAppsRecyclerView)
        binding.pinnedAppsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateFadeOverlays()
            }
        })
        updatePinnedViewportHeight()
        initPinnedAppsGestureBridge()
    }

    private fun initPinnedAppsGestureBridge() {
        val detector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                event1: MotionEvent?,
                event2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val startEvent = event1 ?: return false
                val diffY = event2.y - startEvent.y
                val diffX = event2.x - startEvent.x
                val swipeThreshold = 100
                val velocityThreshold = 100
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                    if (kotlin.math.abs(diffX) > swipeThreshold && kotlin.math.abs(velocityX) > velocityThreshold) {
                        if (diffX > 0) openSwipeRightApp() else openSwipeLeftApp()
                    }
                } else if (kotlin.math.abs(diffY) > swipeThreshold && kotlin.math.abs(velocityY) > velocityThreshold) {
                    if (diffY < 0 && !binding.pinnedAppsRecyclerView.canScrollVertically(1)) {
                        handleSwipeUpAction()
                    } else if (diffY > 0 && !binding.pinnedAppsRecyclerView.canScrollVertically(-1)) {
                        handleSwipeDownAction()
                    }
                }
                return false
            }
        })
        binding.pinnedAppsRecyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                detector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun updatePinnedViewportHeight() {
        val itemHeight = resources.getDimensionPixelSize(R.dimen.home_pinned_item_height)
        binding.homeAppsViewport.layoutParams = binding.homeAppsViewport.layoutParams.apply {
            height = itemHeight * PRIMARY_HOME_APPS
        }
    }

    private fun updateFadeOverlays() {
        val recyclerView = binding.pinnedAppsRecyclerView
        val canScrollUp = recyclerView.canScrollVertically(-1)
        val canScrollDown = recyclerView.canScrollVertically(1)
        binding.topFadeOverlay.visibility = if (canScrollUp) View.VISIBLE else View.GONE
        binding.bottomFadeOverlay.visibility = if (canScrollDown) View.VISIBLE else View.GONE
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = lm.findFirstVisibleItemPosition()
        val lastVisible = lm.findLastVisibleItemPosition()
        if (firstVisible < 0 || lastVisible < 0) return
        val edgeStateChanged = firstVisible != lastEdgeFirstVisible || lastVisible != lastEdgeLastVisible ||
            canScrollUp != lastEdgeCanScrollUp || canScrollDown != lastEdgeCanScrollDown
        if (!edgeStateChanged) return
        lastEdgeFirstVisible = firstVisible
        lastEdgeLastVisible = lastVisible
        lastEdgeCanScrollUp = canScrollUp
        lastEdgeCanScrollDown = canScrollDown
        if (pinnedEdgeTextColor == 0) {
            pinnedEdgeTextColor = requireContext().getColor(R.color.home_pinned_edge_text)
            pinnedNormalTextColor = requireContext().getColor(android.R.color.white)
        }
        for (position in firstVisible..lastVisible) {
            val holder = recyclerView.findViewHolderForAdapterPosition(position) as? PinnedAppsAdapter.PinnedAppViewHolder ?: continue
            val useGray = (position == firstVisible && canScrollUp) || (position == lastVisible && canScrollDown)
            holder.title.setTextColor(if (useGray) pinnedEdgeTextColor else pinnedNormalTextColor)
        }
    }

    private fun getHomeAppInfo(location: Int): HomeAppInfo {
        return HomeAppInfo(
            appName = prefs.getAppName(location),
            packageName = prefs.getAppPackage(location),
            activityClassName = prefs.getAppActivityClassName(location),
            userString = prefs.getAppUser(location),
            isShortcut = prefs.getIsShortcut(location),
            shortcutId = prefs.getShortcutId(location)
        )
    }

    private fun clearHomeAppSlot(location: Int) {
        prefs.clearHomeApp(location)
    }

    private fun handleSwipeUpAction() {
        // Keep swipe-up detection active with no action.
    }

    private fun handleSwipeDownAction() {
        swipeDownAction()
    }

    private fun getHomeAppFlag(location: Int): Int? {
        return when (location) {
            1 -> Constants.FLAG_SET_HOME_APP_1
            2 -> Constants.FLAG_SET_HOME_APP_2
            3 -> Constants.FLAG_SET_HOME_APP_3
            4 -> Constants.FLAG_SET_HOME_APP_4
            5 -> Constants.FLAG_SET_HOME_APP_5
            6 -> Constants.FLAG_SET_HOME_APP_6
            7 -> Constants.FLAG_SET_HOME_APP_7
            8 -> Constants.FLAG_SET_HOME_APP_8
            9 -> Constants.FLAG_SET_HOME_APP_9
            10 -> Constants.FLAG_SET_HOME_APP_10
            11 -> Constants.FLAG_SET_HOME_APP_11
            12 -> Constants.FLAG_SET_HOME_APP_12
            else -> null
        }
    }

    private fun openHomeAppSelector(location: Int) {
        val flag = getHomeAppFlag(location) ?: return
        showAppList(flag, prefs.getAppName(location).isNotEmpty(), true)
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        val homeAppsNum = minOf(prefs.homeAppsNum, MAX_HOME_APPS)
        if (homeAppsNum == 0) {
            pinnedAppsAdapter.submitList(emptyList())
            return
        }

        val pinnedItems = mutableListOf<HomePinnedItem>()
        for (index in 1..homeAppsNum) {
            val appInfo = getHomeAppInfo(index)
            val hasValidApp = isHomeAppValid(
                appInfo.packageName,
                appInfo.userString,
                appInfo.isShortcut,
                appInfo.shortcutId
            )
            if (!hasValidApp) clearHomeAppSlot(index)
            pinnedItems.add(
                HomePinnedItem(
                    location = index,
                    title = if (hasValidApp && appInfo.appName.isNotBlank()) appInfo.appName else getString(R.string.add_an_app)
                )
            )
        }
        pinnedAppsAdapter.submitList(pinnedItems)
    }

    private fun isHomeAppValid(packageName: String, userString: String, isShortcut: Boolean, shortcutId: String?): Boolean {
        // Get user handle for the app/shortcut
        val userHandle = getUserHandleFromString(requireContext(), userString)

        // If it's a shortcut, verify it still exists
        if (isShortcut) {
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            // Query for the specific shortcut
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }

            try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                // Check if our shortcut still exists
                if (shortcuts?.any { it.id == shortcutId } == true) {
                    return true
                }
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        // Regular app check
        return isPackageInstalled(requireContext(), packageName, userString)
    }

    private fun hideHomeApps() {
        if (::pinnedAppsAdapter.isInitialized) pinnedAppsAdapter.submitList(emptyList())
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null
    ) {
        if (appName.isEmpty()) {
            showLongPressToast()
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun homeAppClicked(location: Int) {
        val appInfo = getHomeAppInfo(location)
        launchAppOrShortcut(
            appName = appInfo.appName,
            packageName = appInfo.packageName,
            activityClassName = appInfo.activityClassName,
            shortcutId = appInfo.shortcutId,
            isShortcut = appInfo.isShortcut,
            userString = appInfo.userString
        )
    }

    private fun openSwipeRightApp() {
        openGoogleOrSystemSearch()
    }

    private fun openSwipeLeftApp() {
        showAppList(Constants.FLAG_LAUNCH_APP)
    }

    private fun openGoogleOrSystemSearch() {
        val context = requireContext()
        val packageManager = context.packageManager
        val googleSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            setPackage("com.google.android.googlequicksearchbox")
            putExtra(SearchManager.QUERY, "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (googleSearchIntent.resolveActivity(packageManager) != null) {
                context.startActivity(googleSearchIntent)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fallbackIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (fallbackIntent.resolveActivity(packageManager) != null) {
                context.startActivity(fallbackIntent)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (searchIntent.resolveActivity(packageManager) != null) {
            context.startActivity(searchIntent)
        }
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            e.printStackTrace()
        }
    }

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> openSearch(requireContext())
            else -> expandNotificationDrawer(requireContext())
        }
    }

    private fun lockPhone() {
        requireActivity().runOnUiThread {
            try {
                deviceManager.lockNow()
            } catch (e: SecurityException) {
                requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            } catch (e: Exception) {
                requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
                prefs.lockModeOn = false
            }
        }
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun changeAppTheme() {
        if (prefs.dailyWallpaper.not()) return
        val changedAppTheme = getChangedAppTheme(requireContext(), prefs.appTheme)
        prefs.appTheme = changedAppTheme
        if (prefs.dailyWallpaper) {
            setPlainWallpaperByTheme(requireContext(), changedAppTheme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun openScreenTimeDigitalWellbeing() {
        if (prefs.screenTimeAppPackage.isNotBlank()) {
            launchApp(
                "Screen Time",
                prefs.screenTimeAppPackage,
                prefs.screenTimeAppClassName,
                prefs.screenTimeAppUser
            )
            return
        }
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                handleSwipeUpAction()
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                handleSwipeDownAction()
            }

            override fun onLongClick() {
                super.onLongClick()
                try {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    viewModel.firstOpen(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    binding.lock.performClick()
                else if (prefs.lockModeOn)
                    lockPhone()
            }

            override fun onClick() {
                super.onClick()
                viewModel.checkForMessages.call()
            }
        }
    }

    // ── Battery ──────────────────────────────────────────────────────────

    private fun registerBatteryReceiver() {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = (level * 100) / scale
                binding.batteryProgress.progress = pct
            }
        }
        requireContext().registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try { requireContext().unregisterReceiver(it) } catch (_: Exception) {}
        }
        batteryReceiver = null
    }

    private fun syncBatteryBarWidth() {
        binding.clock.post {
            if (_binding == null) return@post
            val clockWidth = binding.clock.width
            if (clockWidth > 0) {
                val lp = binding.batteryProgress.layoutParams
                lp.width = clockWidth
                binding.batteryProgress.layoutParams = lp
            }
        }
    }

    private fun openBatterySettings() {
        val ctx = requireContext()
        try {
            ctx.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) {}
            }
        }
    }

    // ── Weather ─────────────────────────────────────────────────────────

    private data class WeatherInfo(
        val temp: Double,
        val humidity: Int,
        val visibilityMeters: Double,
        val aqi: Int,
        val weatherCode: Int
    )

    private fun launcherPrefs(): SharedPreferences =
        requireContext().getSharedPreferences(PREF_WEATHER, Context.MODE_PRIVATE)

    private fun refreshWeatherIfNeeded() {
        val sp = launcherPrefs()
        val lastTs = sp.getLong(KEY_TS, 0L)
        if (System.currentTimeMillis() - lastTs < WEATHER_REFRESH_MS) return
        lifecycleScope.launch {
            try {
                val (lat, lon) = getWeatherCoordinates()
                val info = withContext(Dispatchers.IO) { fetchWeatherInfo(lat, lon) } ?: return@launch
                cacheWeatherInfo(info, lat, lon)
                if (_binding != null) applyWeatherInfo(info)
            } catch (_: Exception) {}
        }
    }

    private fun getWeatherCoordinates(): Pair<Double, Double> {
        val cached = getCachedCoordinates()
        try {
            val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = getLastKnownLocation(lm)
            if (loc != null) return loc.latitude to loc.longitude
        } catch (_: SecurityException) {}
        return cached
    }

    @Suppress("MissingPermission")
    private fun getLastKnownLocation(lm: LocationManager): android.location.Location? {
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) return loc
            } catch (_: Exception) {}
        }
        return null
    }

    private fun getCachedCoordinates(): Pair<Double, Double> {
        val sp = launcherPrefs()
        val lat = sp.getFloat(KEY_LAT, DEFAULT_LAT.toFloat()).toDouble()
        val lon = sp.getFloat(KEY_LON, DEFAULT_LON.toFloat()).toDouble()
        return lat to lon
    }

    private fun fetchWeatherInfo(lat: Double, lon: Double): WeatherInfo? {
        val forecastUrl = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,weather_code,visibility"
        val aqiUrl = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$lat&longitude=$lon" +
                "&current=european_aqi"

        val forecastJson = httpGet(forecastUrl) ?: return null
        val current = forecastJson.getJSONObject("current")
        val temp = current.getDouble("temperature_2m")
        val humidity = current.getInt("relative_humidity_2m")
        val vis = current.getDouble("visibility")
        val code = current.getInt("weather_code")

        var aqi = 0
        try {
            val aqiJson = httpGet(aqiUrl)
            aqi = aqiJson?.getJSONObject("current")?.getInt("european_aqi") ?: 0
        } catch (_: Exception) {}

        return WeatherInfo(temp, humidity, vis, aqi, code)
    }

    private fun httpGet(url: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            if (conn.responseCode == 200) JSONObject(conn.inputStream.bufferedReader().readText())
            else null
        } finally {
            conn.disconnect()
        }
    }

    private fun cacheWeatherInfo(info: WeatherInfo, lat: Double, lon: Double) {
        launcherPrefs().edit()
            .putFloat(KEY_TEMP, info.temp.toFloat())
            .putInt(KEY_HUMIDITY, info.humidity)
            .putFloat(KEY_VISIBILITY, info.visibilityMeters.toFloat())
            .putInt(KEY_AQI, info.aqi)
            .putInt(KEY_CODE, info.weatherCode)
            .putLong(KEY_TS, System.currentTimeMillis())
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .apply()
    }

    private fun renderCachedWeather() {
        val sp = launcherPrefs()
        if (!sp.contains(KEY_TS)) return
        val info = WeatherInfo(
            temp = sp.getFloat(KEY_TEMP, 0f).toDouble(),
            humidity = sp.getInt(KEY_HUMIDITY, 0),
            visibilityMeters = sp.getFloat(KEY_VISIBILITY, 0f).toDouble(),
            aqi = sp.getInt(KEY_AQI, 0),
            weatherCode = sp.getInt(KEY_CODE, 0)
        )
        applyWeatherInfo(info)
    }

    private fun applyWeatherInfo(info: WeatherInfo) {
        if (_binding == null) return
        binding.weatherWidget.visibility = View.VISIBLE
        applyWeatherWidgetOffsets()

        val useFahrenheit = useFahrenheit()
        val displayTemp = if (useFahrenheit) info.temp * 9.0 / 5.0 + 32.0 else info.temp
        binding.weatherTemp.text = "${displayTemp.toInt()}°"
        binding.weatherUnit.text = if (useFahrenheit) "f" else "c"
        binding.weatherIcon.setImageResource(getWeatherIconResource(info.weatherCode))
        binding.weatherHumidity.text = "${info.humidity}%"
        binding.weatherVisibility.text = formatVisibility(info.visibilityMeters, useFahrenheit)
        binding.weatherAqi.text = "${info.aqi}"
    }

    /** Applies position offsets from dimens so changing values in dimens.xml takes effect after rebuild. */
    private fun applyWeatherWidgetOffsets() {
        if (_binding == null) return
        val res = resources
        binding.weatherIconWrapper.translationX = res.getDimension(R.dimen.weather_icon_offset_x)
        binding.weatherIconWrapper.translationY = res.getDimension(R.dimen.weather_icon_offset_y)
        binding.weatherTempCapsule.translationX = res.getDimension(R.dimen.weather_temp_offset_x)
        binding.weatherTempCapsule.translationY = res.getDimension(R.dimen.weather_temp_offset_y)
    }

    private fun useFahrenheit(): Boolean {
        val country = Locale.getDefault().country.uppercase()
        return country in listOf("US", "BS", "KY", "LR", "PW", "FM", "MH")
    }

    private fun formatVisibility(meters: Double, imperial: Boolean): String {
        return if (imperial) {
            val miles = meters / 1609.344
            "${miles.toInt()}mi"
        } else {
            val km = meters / 1000.0
            "${km.toInt()}km"
        }
    }

    private fun getWeatherIconResource(code: Int): Int = when (code) {
        0, 1 -> R.drawable.ic_weather_clear_home
        2, 3 -> R.drawable.ic_weather_partly_cloudy_home
        45, 48 -> R.drawable.ic_weather_cloud_home
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> R.drawable.ic_weather_rain_home
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow_home
        else -> R.drawable.ic_weather_cloud_home
    }

    private fun openWeatherPage() {
        val ctx = requireContext()
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.msn.com/weather")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterBatteryReceiver()
        _binding = null
    }
}