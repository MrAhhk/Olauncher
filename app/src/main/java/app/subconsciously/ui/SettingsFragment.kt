package app.subconsciously.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.subconsciously.BuildConfig
import app.subconsciously.reflection.ReflectionAlphabetStrip
import app.subconsciously.reflection.ReflectionConstants
import app.subconsciously.MainActivity
import app.subconsciously.MainViewModel
import app.subconsciously.R
import app.subconsciously.data.Constants
import app.subconsciously.data.Prefs
import app.subconsciously.databinding.FragmentSettingsBinding
import app.subconsciously.helper.ABOUT_URL
import app.subconsciously.helper.appUsagePermissionGranted
import app.subconsciously.helper.getColorFromAttr
import app.subconsciously.helper.isOlauncherDefault
import app.subconsciously.helper.isDeviceLocationEnabled
import app.subconsciously.helper.openAppInfo
import app.subconsciously.helper.openDeviceLocationSettingsOrPanel
import app.subconsciously.helper.openUrl
import app.subconsciously.helper.rateApp
import app.subconsciously.helper.shareApp
import app.subconsciously.helper.showToast

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

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

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populateKeyboardText()
        populateScreenTimeOnOff()
        populateStatusBar()
        populateDateTime()
        populateWeatherWidget()
        populateWeatherTempUnit()
        populateSwipeApps()
        populateSwipeDownAction()
        populateActionHints()
        populateMode()
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
            R.id.updateWeatherButton -> requestLocationThenRefreshWeather()
            R.id.weatherTempUnitText -> binding.weatherTempUnitSelectLayout.visibility = View.VISIBLE
            R.id.weatherTempSystem -> setWeatherTempUnit("system")
            R.id.weatherTempCelsius -> setWeatherTempUnit("celsius")
            R.id.weatherTempFahrenheit -> setWeatherTempUnit("fahrenheit")

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
                requireContext().openUrl(ABOUT_URL)
            }

            R.id.share -> requireActivity().shareApp()
            R.id.star1 -> { fillStars(1); prefs.rateClicked = true; requireActivity().rateApp() }
            R.id.star2 -> { fillStars(2); prefs.rateClicked = true; requireActivity().rateApp() }
            R.id.star3 -> { fillStars(3); prefs.rateClicked = true; requireActivity().rateApp() }
            R.id.star4 -> { fillStars(4); prefs.rateClicked = true; requireActivity().rateApp() }
            R.id.star5 -> { fillStars(5); prefs.rateClicked = true; requireActivity().rateApp() }

            R.id.twitter -> requireContext().openUrl(Constants.URL_TWITTER_TANUJ)
            R.id.github -> requireContext().openUrl(Constants.URL_OLAUNCHER_GITHUB)
            R.id.privacy -> requireContext().openUrl(Constants.URL_OLAUNCHER_PRIVACY)
            R.id.footer -> requireContext().openUrl(Constants.URL_KOFI)
            R.id.tvMode -> showModeDialog()
            R.id.manageDistractionApps -> (requireActivity() as MainActivity).showReflectionSetupDialog()
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
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

        binding.share.setOnClickListener(this)
        binding.star1.setOnClickListener(this)
        binding.star2.setOnClickListener(this)
        binding.star3.setOnClickListener(this)
        binding.star4.setOnClickListener(this)
        binding.star5.setOnClickListener(this)
        binding.twitter.setOnClickListener(this)
        binding.github.setOnClickListener(this)
        binding.privacy.setOnClickListener(this)
        binding.footer.setOnClickListener(this)
        binding.tvMode?.setOnClickListener(this)
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
        val wasEnabled = prefs.showWeatherWidget
        prefs.showWeatherWidget = enabled
        populateWeatherWidget()
        if (enabled && !wasEnabled) requestLocationThenRefreshWeather()
    }

    private fun hasLocationPermission(): Boolean {
        val ctx = requireContext()
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /** Ask for runtime permission if needed; then signal home to refresh (uses cache if device GPS is off). */
    private fun requestLocationThenRefreshWeather() {
        if (!prefs.onboardingComplete) {
            viewModel.requestWeatherRefresh.value = true
            requireContext().showToast(getString(R.string.updating_weather))
            return
        }
        if (hasLocationPermission()) {
            viewModel.requestWeatherRefresh.value = true
            requireContext().showToast(getString(R.string.updating_weather))
            if (!requireContext().isDeviceLocationEnabled()) {
                requireContext().openDeviceLocationSettingsOrPanel()
            }
            return
        }
        (requireActivity() as MainActivity).requestWeatherLocationPermission()
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

    private fun fillStars(count: Int) {
        val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
        stars.forEachIndexed { index, tv ->
            if (index < count) {
                tv.text = "★"
                tv.setTextColor(android.graphics.Color.parseColor("#FFD700"))
            }
        }
    }

    private fun populateActionHints() {
        if (prefs.aboutClicked.not())
            binding.aboutOlauncher.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_info, 0)
        if (viewModel.isOlauncherDefault.value != true) return
    }

    private fun populateMode() {
        binding.tvMode?.text = when (prefs.identityMode) {
            "easy" -> "Easy"
            "hard" -> "HARD"
            else   -> "Normal"
        }
    }

    private fun showModeDialog() {
        val current = prefs.identityMode.ifBlank { "normal" }
        val modes = listOf("easy", "normal", "hard")

        val view = layoutInflater.inflate(R.layout.dialog_mode_select, null)
        val optionViews = listOf(
            view.findViewById<android.widget.LinearLayout>(R.id.modeEasy),
            view.findViewById<android.widget.LinearLayout>(R.id.modeNormal),
            view.findViewById<android.widget.LinearLayout>(R.id.modeHard),
        )
        val btnConfirm = view.findViewById<android.widget.TextView>(R.id.btnModeConfirm)

        var selected = current

        fun highlight(key: String) {
            modes.forEachIndexed { i, mode ->
                optionViews[i].setBackgroundResource(
                    if (mode == key) R.drawable.bg_onboarding_option_selected
                    else R.drawable.bg_onboarding_option
                )
            }
        }
        highlight(selected)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.ReflectionSetupDialog)
            .setView(view)
            .setCancelable(true)
            .create()

        modes.forEachIndexed { i, mode ->
            optionViews[i].setOnClickListener {
                selected = mode
                highlight(selected)
            }
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            if (selected == current) return@setOnClickListener

            val isUpgrade = modes.indexOf(selected) > modes.indexOf(current)
            if (isUpgrade) {
                showModeConfirmDialog(
                    message = "Go for it? You won't be able to lower it for 3 days.",
                    primaryLabel = "Okay",
                    secondaryLabel = "Back",
                    onPrimary = {
                        prefs.identityMode = selected
                        prefs.modeUpgradeTimestamp = System.currentTimeMillis()
                        populateMode()
                    },
                )
            } else {
                val elapsed = System.currentTimeMillis() - prefs.modeUpgradeTimestamp
                val threeDaysMs = 3L * 24 * 60 * 60 * 1000L
                if (prefs.modeUpgradeTimestamp > 0L && elapsed < threeDaysMs) {
                    val hourMs = 60 * 60 * 1000L
                    val hoursLeft = (threeDaysMs - elapsed + hourMs - 1L) / hourMs
                    showModeConfirmDialog(
                        message = "You're locked in for $hoursLeft ${if (hoursLeft == 1L) "hour" else "hours"}. Stay with it!",
                        primaryLabel = "Ok",
                    )
                } else {
                    showModeConfirmDialog(
                        message = "Switch to an easier mode?",
                        primaryLabel = "Yes",
                        secondaryLabel = "No",
                        onPrimary = {
                            prefs.identityMode = selected
                            populateMode()
                        },
                    )
                }
            }
        }

        dialog.show()
        ReflectionAlphabetStrip.styleDialogWindow(dialog, ReflectionConstants.DIALOG_WIDTH_FRACTION_MAIN)
    }

    private fun showModeConfirmDialog(
        message: String,
        primaryLabel: String,
        secondaryLabel: String? = null,
        onPrimary: (() -> Unit)? = null,
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_mode_confirm, null)
        val tvMessage = view.findViewById<android.widget.TextView>(R.id.confirmMessage)
        val btnPrimary = view.findViewById<android.widget.TextView>(R.id.btnConfirmPrimary)
        val btnSecondary = view.findViewById<android.widget.TextView>(R.id.btnConfirmSecondary)

        tvMessage.text = message
        btnPrimary.text = primaryLabel

        if (secondaryLabel != null) {
            btnSecondary.text = secondaryLabel
            btnSecondary.visibility = android.view.View.VISIBLE
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.ReflectionSetupDialog)
            .setView(view)
            .setCancelable(true)
            .create()

        btnPrimary.setOnClickListener {
            dialog.dismiss()
            onPrimary?.invoke()
        }
        btnSecondary.setOnClickListener { dialog.dismiss() }

        dialog.show()
        ReflectionAlphabetStrip.styleDialogWindow(dialog, ReflectionConstants.DIALOG_WIDTH_FRACTION_MAIN)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}