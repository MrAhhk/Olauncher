package app.olauncher.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.SearchManager

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    companion object {
        private const val MAX_HOME_APPS = 12
        private const val PRIMARY_HOME_APPS = 7
    }

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var pinnedAppsAdapter: PinnedAppsAdapter

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
    }

    override fun onResume() {
        super.onResume()
        populateHomeScreen(false)
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
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

//        var dateText = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var dateText = dateFormat.format(Date())

        if (!prefs.showStatusBar) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0)
                dateText = getString(R.string.day_battery, dateText, battery)
        }
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
            holder.itemView.tag = item.location
            holder.itemView.setOnClickListener(this@HomeFragment)
            holder.itemView.setOnLongClickListener(this@HomeFragment)
        }

        override fun getItemCount(): Int = items.size

        fun submitList(newItems: List<HomePinnedItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
            updateFadeOverlays()
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
        val canScrollUp = binding.pinnedAppsRecyclerView.canScrollVertically(-1)
        val canScrollDown = binding.pinnedAppsRecyclerView.canScrollVertically(1)
        binding.topFadeOverlay.visibility = if (canScrollUp) View.VISIBLE else View.GONE
        binding.bottomFadeOverlay.visibility = if (canScrollDown) View.VISIBLE else View.GONE
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
        showAppList(Constants.FLAG_LAUNCH_APP)
    }

    private fun openSwipeLeftApp() {
        openGoogleOrSystemSearch()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}