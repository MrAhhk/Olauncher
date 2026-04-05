package app.olauncher.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.BuildConfig
import app.olauncher.MainActivity
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentSettingsBinding
import app.olauncher.helper.animateAlpha
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.helper.rateApp
import app.olauncher.helper.shareApp
import app.olauncher.helper.showToast
import app.olauncher.listener.DeviceAdmin

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private val addDeviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && ::prefs.isInitialized)
            prefs.lockModeOn = true
    }

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        deviceManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)
        checkAdminPermission()

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populateKeyboardText()
        populateScreenTimeOnOff()
        populateLockSettings()
        populateStatusBar()
        populateDateTime()
        populateWeatherWidget()
        populateWeatherTempUnit()
        populateSwipeApps()
        populateSwipeDownAction()
        populateActionHints()
        initClickListeners()
        initObservers()
        if (prefs.firstSettingsOpen)
            prefs.firstSettingsOpen = false
    }

    override fun onClick(view: View) {
        binding.appsNumSelectLayout.visibility = View.GONE
        binding.dateTimeSelectLayout.visibility = View.GONE
        binding.weatherWidgetSelectLayout.visibility = View.GONE
        binding.weatherTempUnitSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE

        when (view.id) {
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.toggleLock -> toggleLockMode()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.homeAppsNum -> binding.appsNumSelectLayout.visibility = View.VISIBLE
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> binding.dateTimeSelectLayout.visibility = View.VISIBLE
            R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
            R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
            R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            R.id.weatherWidgetOnOff -> binding.weatherWidgetSelectLayout.visibility = View.VISIBLE
            R.id.weatherWidgetOn -> setWeatherWidgetEnabled(true)
            R.id.weatherWidgetOff -> setWeatherWidgetEnabled(false)
            R.id.updateWeatherButton -> {
                viewModel.requestWeatherRefresh.value = true
                requireContext().showToast(getString(R.string.updating_weather))
            }
            R.id.weatherTempUnitText -> binding.weatherTempUnitSelectLayout.visibility = View.VISIBLE
            R.id.weatherTempSystem -> setWeatherTempUnit("system")
            R.id.weatherTempCelsius -> setWeatherTempUnit("celsius")
            R.id.weatherTempFahrenheit -> setWeatherTempUnit("fahrenheit")
            R.id.actionAccessibility -> openAccessibilityService()
            R.id.closeAccessibility -> toggleAccessibilityVisibility(false)
            R.id.notWorking -> requireContext().openUrl(Constants.URL_DOUBLE_TAP)

            R.id.tvGestures -> binding.flSwipeDown.visibility = View.VISIBLE

            R.id.maxApps0 -> updateHomeAppsNum(0)
            R.id.maxApps1 -> updateHomeAppsNum(1)
            R.id.maxApps2 -> updateHomeAppsNum(2)
            R.id.maxApps3 -> updateHomeAppsNum(3)
            R.id.maxApps4 -> updateHomeAppsNum(4)
            R.id.maxApps5 -> updateHomeAppsNum(5)
            R.id.maxApps6 -> updateHomeAppsNum(6)
            R.id.maxApps7 -> updateHomeAppsNum(7)
            R.id.maxApps8 -> updateHomeAppsNum(8)
            R.id.maxApps9 -> updateHomeAppsNum(9)
            R.id.maxApps10 -> updateHomeAppsNum(10)
            R.id.maxApps11 -> updateHomeAppsNum(11)
            R.id.maxApps12 -> updateHomeAppsNum(12)

            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)
            R.id.swipeDownAction -> binding.swipeDownSelectLayout.visibility = View.VISIBLE
            R.id.notifications -> updateSwipeDownAction(Constants.SwipeDownAction.NOTIFICATIONS)
            R.id.search -> updateSwipeDownAction(Constants.SwipeDownAction.SEARCH)

            R.id.aboutOlauncher -> {
                prefs.aboutClicked = true
                requireContext().openUrl(Constants.URL_ABOUT_OLAUNCHER)
            }

            R.id.share -> requireActivity().shareApp()
            R.id.rate -> {
                prefs.rateClicked = true
                requireActivity().rateApp()
            }

            R.id.twitter -> requireContext().openUrl(Constants.URL_TWITTER_TANUJ)
            R.id.github -> requireContext().openUrl(Constants.URL_OLAUNCHER_GITHUB)
            R.id.privacy -> requireContext().openUrl(Constants.URL_OLAUNCHER_PRIVACY)
            R.id.footer -> requireContext().openUrl(Constants.URL_NTS)
            R.id.manageDistractionApps -> (requireActivity() as MainActivity).showReflectionSetupDialog()
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    private fun initClickListeners() {
        binding.olauncherHiddenApps.setOnClickListener(this)
        binding.scrollLayout.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.aboutOlauncher.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.toggleLock.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.dateTimeOn.setOnClickListener(this)
        binding.dateTimeOff.setOnClickListener(this)
        binding.dateOnly.setOnClickListener(this)
        binding.weatherWidgetOnOff.setOnClickListener(this)
        binding.updateWeatherButton.setOnClickListener(this)
        binding.weatherWidgetOn.setOnClickListener(this)
        binding.weatherWidgetOff.setOnClickListener(this)
        binding.weatherTempUnitText.setOnClickListener(this)
        binding.weatherTempSystem.setOnClickListener(this)
        binding.weatherTempCelsius.setOnClickListener(this)
        binding.weatherTempFahrenheit.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        binding.actionAccessibility.setOnClickListener(this)
        binding.closeAccessibility.setOnClickListener(this)
        binding.notWorking.setOnClickListener(this)

        binding.share.setOnClickListener(this)
        binding.rate.setOnClickListener(this)
        binding.twitter.setOnClickListener(this)
        binding.github.setOnClickListener(this)
        binding.privacy.setOnClickListener(this)
        binding.footer.setOnClickListener(this)
        binding.manageDistractionApps.setOnClickListener(this)

        binding.maxApps0.setOnClickListener(this)
        binding.maxApps1.setOnClickListener(this)
        binding.maxApps2.setOnClickListener(this)
        binding.maxApps3.setOnClickListener(this)
        binding.maxApps4.setOnClickListener(this)
        binding.maxApps5.setOnClickListener(this)
        binding.maxApps6.setOnClickListener(this)
        binding.maxApps7.setOnClickListener(this)
        binding.maxApps8.setOnClickListener(this)
        binding.maxApps9.setOnClickListener(this)
        binding.maxApps10.setOnClickListener(this)
        binding.maxApps11.setOnClickListener(this)
        binding.maxApps12.setOnClickListener(this)

        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        binding.toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            if (it) {
                binding.setLauncher.text = getString(R.string.change_default_launcher)
                prefs.toShowHintCounter += 1
            }
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_left_app_enabled))
        } else {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_left_app_disabled))
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_right_app_enabled))
        } else {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_right_app_disabled))
        }
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            showStatusBar()
            binding.statusBar.text = getString(R.string.on)
        } else {
            hideStatusBar()
            binding.statusBar.text = getString(R.string.off)
        }
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        binding.dateTime.text = getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
    }

    private fun setWeatherTempUnit(unit: String) {
        prefs.weatherTempUnit = unit
        populateWeatherTempUnit()
    }

    private fun setWeatherWidgetEnabled(enabled: Boolean) {
        prefs.showWeatherWidget = enabled
        populateWeatherWidget()
    }

    private fun populateWeatherWidget() {
        binding.weatherWidgetOnOff.text = getString(
            if (prefs.showWeatherWidget) R.string.on else R.string.off
        )
        binding.updateWeatherButton.isVisible = prefs.showWeatherWidget
    }

    private fun populateWeatherTempUnit() {
        binding.weatherTempUnitText.text = getString(
            when (prefs.weatherTempUnit) {
                "celsius" -> R.string.temp_celsius
                "fahrenheit" -> R.string.temp_fahrenheit
                else -> R.string.temp_system
            }
        )
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

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            requireContext().showToast(getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun checkAdminPermission() {
        val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            prefs.lockModeOn = isAdmin
    }

    private fun toggleAccessibilityVisibility(show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            binding.notWorking.visibility = View.VISIBLE
        if (isAccessServiceEnabled(requireContext()))
            binding.actionAccessibility.text = getString(R.string.disable)
        binding.accessibilityLayout.isVisible = show
        binding.scrollView.animateAlpha(if (show) 0.5f else 1f)
    }

    private fun openAccessibilityService() {
        toggleAccessibilityVisibility(false)
        // prefs.lockModeOn = true
        populateLockSettings()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleLockMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            toggleAccessibilityVisibility(true)
            if (prefs.lockModeOn) {
                prefs.lockModeOn = false
                removeActiveAdmin()
            }
        } else {
            val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
            if (isAdmin) {
                removeActiveAdmin("Admin permission removed.")
                prefs.lockModeOn = false
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_permission_message)
                )
                addDeviceAdminLauncher.launch(intent)
            }
        }
        populateLockSettings()
    }

    private fun removeActiveAdmin(toastMessage: String? = null) {
        try {
            deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            requireContext().showToast(toastMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        binding.appsNumSelectLayout.visibility = View.GONE
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (requireContext().appUsagePermissionGranted()) binding.screenTimeOnOff.text = getString(R.string.on)
            else binding.screenTimeOnOff.text = getString(R.string.off)
        } else binding.screenTimeLayout.visibility = View.GONE
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = getString(R.string.on)
        else binding.autoShowKeyboard.text = getString(R.string.off)
    }

    private fun populateLockSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.toggleLock.text = getString(
                if (isAccessServiceEnabled(requireContext())) R.string.on
                else R.string.off
            )
        } else {
            binding.toggleLock.text = getString(
                if (prefs.lockModeOn) R.string.on
                else R.string.off
            )
        }
    }

    private fun populateSwipeDownAction() {
        binding.swipeDownAction.text = when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.NOTIFICATIONS -> getString(R.string.notifications)
            else -> getString(R.string.search)
        }
    }

    private fun updateSwipeDownAction(swipeDownFor: Int) {
        if (prefs.swipeDownAction == swipeDownFor) return
        prefs.swipeDownAction = swipeDownFor
        populateSwipeDownAction()
    }

    private fun populateSwipeApps() {
        binding.swipeLeftRow.visibility = View.GONE
        binding.swipeRightRow.visibility = View.GONE
        binding.swipeLeftApp.text = prefs.appNameSwipeLeft
        binding.swipeRightApp.text = prefs.appNameSwipeRight
        if (!prefs.swipeLeftEnabled)
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        if (!prefs.swipeRightEnabled)
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
    }

//    private fun populateDigitalWellbeing() {
//        binding.digitalWellbeing.isVisible = requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_PACKAGE_NAME).not()
//                && requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME).not()
//                && prefs.hideDigitalWellbeing.not()
//    }

    private fun showAppListIfEnabled(flag: Int) {
        if ((flag == Constants.FLAG_SET_SWIPE_LEFT_APP) and !prefs.swipeLeftEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        if ((flag == Constants.FLAG_SET_SWIPE_RIGHT_APP) and !prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        viewModel.getAppList(true)
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }

    private fun populateActionHints() {
        if (prefs.aboutClicked.not())
            binding.aboutOlauncher.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_info, 0)
        if (viewModel.isOlauncherDefault.value != true) return
        if (prefs.rateClicked.not() && prefs.toShowHintCounter > Constants.HINT_RATE_US && prefs.toShowHintCounter < Constants.HINT_RATE_US + 100)
            binding.rate.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.arrow_down_float, 0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}