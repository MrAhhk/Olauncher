package app.subconsciously

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import app.subconsciously.data.Constants
import app.subconsciously.data.DistractionList
import app.subconsciously.data.Prefs
import app.subconsciously.databinding.ActivityMainBinding
import app.subconsciously.databinding.DialogMilestoneBinding
import app.subconsciously.databinding.DialogReflectionSetupBinding
import app.subconsciously.helper.getColorFromAttr
import app.subconsciously.helper.hasBeenHours
import app.subconsciously.helper.isDeviceLocationEnabled
import app.subconsciously.helper.openDeviceLocationSettingsOrPanel
import app.subconsciously.helper.isDarkThemeOn
import app.subconsciously.helper.isDefaultLauncher
import app.subconsciously.helper.isEinkDisplay
import app.subconsciously.helper.isOlauncherDefault
import app.subconsciously.helper.isTablet
import app.subconsciously.helper.openUrl
import app.subconsciously.helper.PlayIntegrityHelper
import app.subconsciously.helper.ReflectionBackgroundManager
import app.subconsciously.helper.rateApp
import app.subconsciously.helper.resetLauncherViaFakeActivity
import app.subconsciously.helper.setPlainWallpaper
import app.subconsciously.helper.shareApp
import app.subconsciously.reflection.ReflectionAlphabetStrip
import app.subconsciously.reflection.ReflectionAppListAdapter
import app.subconsciously.reflection.ReflectionConstants
import app.subconsciously.reflection.ReflectionSetupRows
import app.subconsciously.reflection.ReflectionUntickPauseDialog
import app.subconsciously.helper.showToast
import app.subconsciously.helper.DeviceIntegrityResult
import app.subconsciously.ui.ReflectionSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class MainActivity : AppCompatActivity() {

    companion object {
        private const val AUTO_LAUNCHER_PROMPT_COOLDOWN_MS = 12_000L
        /** If we return to MainActivity sooner than this after [onStop], treat it as a transient system UI (e.g. home role), not a real "left the app" return. */
        private const val SHORT_BACKGROUND_FOR_LAUNCHER_NUDGE_MS = 1_500L
    }

    /**
     * Prevents a tight loop: onResume → open "set default launcher" UI → return → onResume → open again,
     * which can freeze the app (not a memory leak). Manual "set default" from Home still calls
     * [MainViewModel.resetLauncherLiveData] directly and is unaffected.
     */
    private var lastAutoLauncherPromptElapsedRealtime = 0L
    private var lastOnStopElapsedRealtime = 0L
    /** One automatic "set default launcher" right after onboarding; avoids missing the prompt when stop→resume is very short. */
    private var offerLauncherImmediatelyAfterOnboarding = false
    /** Avoid repeated PackageManager.getPackageInfo on every resume when [Prefs.firstOpenTime] is still 0. */
    private var installTimeBackfillAttempted = false
    private var integrityCheckStarted = false

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController

    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK)
            resetLauncherViaFakeActivity()
    }

    private val onboardingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            prefs.onboardingComplete = true
            prefs.reflectionSetupDone = true
            if (!isDefaultLauncher()) {
                offerLauncherImmediatelyAfterOnboarding = true
            }
        }
    }

    /**
     * Host-level permission request so the result is delivered reliably (Settings sits above Home on the back stack;
     * Fragment-owned launchers can miss callbacks in that setup).
     */
    private val requestWeatherLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            viewModel.requestWeatherRefresh.value = true
            showToast(getString(R.string.updating_weather))
            if (!isDeviceLocationEnabled()) {
                openDeviceLocationSettingsOrPanel()
            }
        }
    }

    fun requestWeatherLocationPermission() {
        requestWeatherLocationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null

//    override fun onBackPressed() {
//        if (navController.currentDestination?.id != R.id.mainFragment)
//            super.onBackPressed()
//    }

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Preload reflection overlay backgrounds (Pexels → disk cache, idempotent).
        ReflectionBackgroundManager.ensureCached(applicationContext)

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.currentDestination?.id != R.id.mainFragment) {
                    // then we might want to finish the activity or disable this callback.
                    if (navController.popBackStack()) {
                        // Successfully popped back
                    } else {
                        // if you want other system/activity level handling
                    }
                } else {
                    binding.messageLayout.visibility = View.GONE
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
            viewModel.setDefaultClockApp()
        }

        initClickListeners()
        initObservers(viewModel)
        viewModel.getAppList()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

        // Avoid stacking a second OnboardingActivity when MainActivity is recreated (rotation, etc.)
        // while onboarding is still incomplete in prefs.
        if (savedInstanceState == null) {
            if (!prefs.onboardingComplete) {
                onboardingLauncher.launch(Intent(this, app.subconsciously.ui.OnboardingActivity::class.java))
            } else if (!prefs.reflectionSetupDone) {
                showReflectionSetupDialog(isInitialSetup = true)
            }
        }
    }

    /**
     * Install-time milestones (session 5). Uses [Prefs.firstOpenTime] epoch ms (set on first launch).
     * If user is eligible for the 90-day prompt, the 30-day prompt is skipped/marked done as well.
     */
    private fun maybeShowInstallMilestoneDialogs() {
        var installMs = prefs.firstOpenTime
        if (installMs <= 0L && !installTimeBackfillAttempted) {
            installTimeBackfillAttempted = true
            installMs = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).firstInstallTime
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).firstInstallTime
                }
            } catch (_: Exception) {
                0L
            }
            if (installMs > 0L) prefs.firstOpenTime = installMs
        }
        if (installMs <= 0L) return
        val daysSinceInstall = (System.currentTimeMillis() - installMs) / 86400000L
        if (daysSinceInstall >= 90 && !prefs.shown90DayMessage) {
            show90DayMilestoneDialog()
        } else if (daysSinceInstall >= 30 && !prefs.shown30DayMessage) {
            show30DayMilestoneDialog()
        }
    }

    /** Style after [AlertDialog.show] so width/dim apply in one frame (avoids flicker vs [Dialog.setOnShowListener]). */
    private fun showMilestoneAlert(dialog: AlertDialog) {
        dialog.show()
        ReflectionAlphabetStrip.styleDialogWindow(dialog, ReflectionConstants.DIALOG_WIDTH_FRACTION_MAIN)
    }

    private fun show30DayMilestoneDialog() {
        val b = DialogMilestoneBinding.inflate(layoutInflater)
        b.milestoneTitle.visibility = View.VISIBLE
        b.milestoneTitle.text = "Have you changed?"
        b.milestoneScroll.visibility = View.GONE
        b.milestoneChoices.visibility = View.GONE
        b.milestoneBtnPrimary.visibility = View.VISIBLE
        b.milestoneBtnPrimary.text = "YES"
        b.milestoneBtnSecondary.visibility = View.VISIBLE
        b.milestoneBtnSecondary.text = "NOT YET"
        val dialog = AlertDialog.Builder(this, R.style.MilestoneDialogTheme)
            .setView(b.root)
            .setCancelable(false)
            .create()
        b.milestoneBtnPrimary.setOnClickListener {
            prefs.shown30DayMessage = true
            val msg =
                "30 days of choosing differently.\n\nThat's not habit — that's character.\n\nThe phone is a tool now.\nYou are the one holding it.\n\nThis app did its job.\nNow so can you."
            dialog.setOnDismissListener {
                dialog.setOnDismissListener(null)
                showMilestoneFollowUpDialog(msg)
            }
            dialog.dismiss()
        }
        b.milestoneBtnSecondary.setOnClickListener {
            prefs.shown30DayMessage = true
            val msg =
                "30 days.\n\nYou kept coming back.\nThat's the whole practice.\n\nChange doesn't announce itself —\nit shows up in the pauses\nyou didn't know you were taking.\n\nKeep going.\nYou're not behind."
            dialog.setOnDismissListener {
                dialog.setOnDismissListener(null)
                showMilestoneFollowUpDialog(msg)
            }
            dialog.dismiss()
        }
        showMilestoneAlert(dialog)
    }

    private fun show90DayMilestoneDialog() {
        val options = listOf(
            "I'm different now",
            "Still fighting",
            "I relapsed but came back",
            "I don't know"
        )
        val responses = listOf(
            "Then you've done the real work. Most people never get here.",
            "Good. The fight is the practice. Keep showing up.",
            "Coming back is the skill. Not everyone does.",
            "That's honest.\n\nNot knowing means you're still paying attention.\nMost people stop asking.\n\nThe answer will come\nwhen it's ready."
        )
        val b = DialogMilestoneBinding.inflate(layoutInflater)
        b.milestoneTitle.visibility = View.VISIBLE
        b.milestoneTitle.text = "Are you still going?"
        b.milestoneScroll.visibility = View.GONE
        b.milestoneBtnPrimary.visibility = View.GONE
        b.milestoneBtnSecondary.visibility = View.GONE
        b.milestoneChoices.visibility = View.VISIBLE
        val dialog = AlertDialog.Builder(this, R.style.MilestoneDialogTheme)
            .setView(b.root)
            .setCancelable(false)
            .create()
        val gapPx = (8 * resources.displayMetrics.density).toInt()
        options.forEachIndexed { idx, label ->
            val row = layoutInflater.inflate(R.layout.item_milestone_choice, b.milestoneChoices, false)
            row.findViewById<TextView>(R.id.choiceLabel).text = label
            row.setOnClickListener {
                prefs.shown90DayMessage = true
                prefs.shown30DayMessage = true
                val msg = responses[idx]
                dialog.setOnDismissListener {
                    dialog.setOnDismissListener(null)
                    showMilestoneFollowUpDialog(msg)
                }
                dialog.dismiss()
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (idx < options.lastIndex) bottomMargin = gapPx
            }
            b.milestoneChoices.addView(row, lp)
        }
        showMilestoneAlert(dialog)
    }

    private fun showMilestoneFollowUpDialog(message: String) {
        val b = DialogMilestoneBinding.inflate(layoutInflater)
        b.milestoneTitle.visibility = View.GONE
        b.milestoneScroll.visibility = View.VISIBLE
        (b.milestoneScroll.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = 0
        b.milestoneBody.text = message
        b.milestoneChoices.visibility = View.GONE
        b.milestoneBtnSecondary.visibility = View.GONE
        b.milestoneBtnPrimary.visibility = View.VISIBLE
        b.milestoneBtnPrimary.text = "OK"
        val dialog = AlertDialog.Builder(this, R.style.MilestoneDialogTheme)
            .setView(b.root)
            .setCancelable(false)
            .create()
        b.milestoneBtnPrimary.setOnClickListener { dialog.dismiss() }
        showMilestoneAlert(dialog)
    }

    /**
     * @param isInitialSetup First launch list (suggested apps): no 6s untick pause. From Settings, use default false.
     */
    fun showReflectionSetupDialog(isInitialSetup: Boolean = false) {
        val distractionList = DistractionList(this)
        val hiddenSuffix = getString(R.string.reflection_list_hidden_suffix)

        lifecycleScope.launch {
            val (installedApps, rows) = withContext(Dispatchers.IO) {
                val apps = distractionList.getAllAppsForReflectionSetup()
                val r = if (apps.isEmpty()) null
                        else ReflectionSetupRows.build(distractionList, prefs, apps, hiddenSuffix)
                Pair(apps, r)
            }

            if (installedApps.isEmpty() || rows == null) {
                prefs.reflectionSetupDone = true
                return@launch
            }

            val binding = DialogReflectionSetupBinding.inflate(layoutInflater)
            lateinit var adapter: ReflectionAppListAdapter
            val useUntickPauseDialog = !isInitialSetup
            adapter = ReflectionAppListAdapter(
                rows = rows,
                useUntickPauseDialog = useUntickPauseDialog,
                onUntickAttempt = { position ->
                    ReflectionUntickPauseDialog.show(this@MainActivity, adapter, rows, position)
                },
            )
            binding.reflectionAppsList.layoutManager = LinearLayoutManager(this@MainActivity)
            binding.reflectionAppsList.adapter = adapter
            ReflectionAlphabetStrip.attach(this@MainActivity, binding, rows, hiddenSuffix)

            val dialog = AlertDialog.Builder(this@MainActivity, R.style.ReflectionSetupDialog)
                .setView(binding.root)
                .setCancelable(false)
                .create()

            binding.reflectionDialogDone.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    distractionList.applyReflectionSelectionBatch(rows)
                    prefs.reflectionSetupDone = true
                    withContext(Dispatchers.Main) { dialog.dismiss() }
                }
            }

            dialog.show()
            ReflectionAlphabetStrip.styleDialogWindow(dialog, ReflectionConstants.DIALOG_WIDTH_FRACTION_MAIN)
        }
    }

    override fun onStart() {
        super.onStart()
        restartLauncherOrCheckTheme()
    }

    override fun onStop() {
        backToHomeScreen()
        super.onStop()
        if (!isChangingConfigurations) {
            lastOnStopElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        backToHomeScreen()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (prefs.dailyWallpaper && AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            setPlainWallpaper()
            viewModel.setWallpaperWorker()
            recreate()
        }
    }

    private fun initClickListeners() {
        binding.ivClose.setOnClickListener {
            binding.messageLayout.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.isOlauncherDefault()
        if (!prefs.onboardingComplete || isDefaultLauncher()) return
        val now = SystemClock.elapsedRealtime()

        if (offerLauncherImmediatelyAfterOnboarding) {
            offerLauncherImmediatelyAfterOnboarding = false
            lastAutoLauncherPromptElapsedRealtime = now
            viewModel.resetLauncherLiveData.call()
            return
        }

        // Only auto-nudge after a real background stint — not immediately after closing role / picker (brief onStop).
        val msSinceStop =
            if (lastOnStopElapsedRealtime > 0L) now - lastOnStopElapsedRealtime else Long.MAX_VALUE
        if (msSinceStop < SHORT_BACKGROUND_FOR_LAUNCHER_NUDGE_MS) return
        if (now - lastAutoLauncherPromptElapsedRealtime < AUTO_LAUNCHER_PROMPT_COOLDOWN_MS) return
        lastAutoLauncherPromptElapsedRealtime = now
        viewModel.resetLauncherLiveData.call()
    }

    override fun onPostResume() {
        super.onPostResume()
        maybeCheckDeviceIntegrity()
        if (!prefs.onboardingComplete || !prefs.reflectionSetupDone) return
        if (prefs.shown30DayMessage && prefs.shown90DayMessage) return
        if (!isFinishing && !isDestroyed) maybeShowInstallMilestoneDialogs()
    }

    private fun maybeCheckDeviceIntegrity() {
        if (integrityCheckStarted) return
        integrityCheckStarted = true
        PlayIntegrityHelper.checkDeviceIntegrity(this) { result ->
            if (isFinishing || isDestroyed) return@checkDeviceIntegrity
            when (result) {
                DeviceIntegrityResult.MeetsDeviceIntegrity -> {
                    // Keep UX silent on success.
                }
                DeviceIntegrityResult.DoesNotMeetDeviceIntegrity -> {
                    showToast("Device integrity check failed")
                }
                DeviceIntegrityResult.NotConfigured -> {
                    // No cloud project configured, skip in local/dev setups.
                }
                is DeviceIntegrityResult.Error -> {
                    showToast("Integrity check unavailable: ${result.message}")
                }
            }
        }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher()) return@observe
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
                if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME))
                    homeRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                else
                    resetLauncherViaFakeActivity()
            } else
                resetLauncherViaFakeActivity()
        }
        viewModel.showReflection.observe(this) {
            if (supportFragmentManager.findFragmentByTag("reflection") == null)
                ReflectionSheet.newInstance().show(supportFragmentManager, "reflection")
        }
        viewModel.checkForMessages.observe(this) {
            checkForMessages()
        }
        viewModel.showDialog.observe(this) {
            when (it) {
                Constants.Dialog.ABOUT -> {
                    showMessageDialog(R.string.app_name, R.string.welcome_to_olauncher_settings, R.string.okay) {
                        binding.messageLayout.visibility = View.GONE
                    }
                }

                Constants.Dialog.WALLPAPER -> {
                    prefs.wallpaperMsgShown = true
                    prefs.userState = Constants.UserState.REVIEW
                    showMessageDialog(R.string.did_you_know, R.string.wallpaper_message, R.string.enable) {
                        prefs.dailyWallpaper = true
                        viewModel.setWallpaperWorker()
                        showToast(getString(R.string.your_wallpaper_will_update_shortly))
                    }
                }

                Constants.Dialog.REVIEW -> {
                    prefs.userState = Constants.UserState.RATE
                    showMessageDialog(R.string.hey, R.string.review_message, R.string.leave_a_review) {
                        prefs.rateClicked = true
                        showToast("😇❤️")
                        rateApp()
                    }
                }

                Constants.Dialog.RATE -> {
                    prefs.userState = Constants.UserState.SHARE
                    showMessageDialog(R.string.app_name, R.string.rate_us_message, R.string.rate_now) {
                        prefs.rateClicked = true
                        showToast("🤩❤️")
                        rateApp()
                    }
                }

                Constants.Dialog.SHARE -> {
                    prefs.shareShownTime = System.currentTimeMillis()
                    showMessageDialog(R.string.hey, R.string.share_message, R.string.share_now) {
                        showToast("😊❤️")
                        shareApp()
                    }
                }

                Constants.Dialog.HIDDEN -> {
                    showMessageDialog(R.string.hidden_apps, R.string.hidden_apps_message, R.string.okay) {
                    }
                }

                Constants.Dialog.KEYBOARD -> {
                    showMessageDialog(R.string.app_name, R.string.keyboard_message, R.string.okay) {
                    }
                }

                Constants.Dialog.DIGITAL_WELLBEING -> {
                    showMessageDialog(R.string.screen_time, R.string.app_usage_message, R.string.permission) {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
            }
        }
    }

    private fun showMessageDialog(title: Int, message: Int, action: Int, clickListener: () -> Unit) {
        binding.tvTitle.text = getString(title)
        binding.tvMessage.text = getString(message)
        binding.tvAction.text = getString(action)
        binding.tvAction.setOnClickListener {
            clickListener()
            binding.messageLayout.visibility = View.GONE
        }
        binding.messageLayout.visibility = View.VISIBLE
    }

    private fun checkForMessages() {
        if (prefs.firstOpenTime == 0L)
            prefs.firstOpenTime = System.currentTimeMillis()
        // Engagement prompts (wallpaper, review, rate, share, new-year) disabled — no popups from timers or navigation.
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
            return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        try {
            binding.messageLayout.visibility = View.GONE
        } catch (e: Exception) { }
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun setPlainWallpaper() {
        if (this.isDarkThemeOn())
            setPlainWallpaper(this, android.R.color.black)
        else setPlainWallpaper(this, android.R.color.white)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun restartLauncherOrCheckTheme(forceRestart: Boolean = false) {
        if (forceRestart || prefs.launcherRestartTimestamp.hasBeenHours(4)) {
            prefs.launcherRestartTimestamp = System.currentTimeMillis()
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try { cacheDir.deleteRecursively() } catch (e: Exception) { }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) recreate()
                }
            }
        } else
            checkTheme()
    }

    private fun checkTheme() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(200)
            if ((prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.white))
                || (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_NO && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.black))
            )
                restartLauncherOrCheckTheme(true)
        }
    }

}