package app.olauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
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
import app.olauncher.data.Constants
import app.olauncher.data.DistractionList
import app.olauncher.data.Prefs
import app.olauncher.databinding.ActivityMainBinding
import app.olauncher.databinding.DialogReflectionSetupBinding
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hasBeenHours
import app.olauncher.helper.isDarkThemeOn
import app.olauncher.helper.isDefaultLauncher
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isTablet
import app.olauncher.helper.openUrl
import app.olauncher.helper.rateApp
import app.olauncher.helper.resetLauncherViaFakeActivity
import app.olauncher.helper.setPlainWallpaper
import app.olauncher.helper.shareApp
import app.olauncher.reflection.ReflectionAlphabetStrip
import app.olauncher.reflection.ReflectionAppListAdapter
import app.olauncher.reflection.ReflectionConstants
import app.olauncher.reflection.ReflectionSetupRows
import app.olauncher.reflection.ReflectionUntickPauseDialog
import app.olauncher.helper.showLauncherSelector
import app.olauncher.helper.showToast
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

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController

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
                onboardingLauncher.launch(Intent(this, app.olauncher.ui.OnboardingActivity::class.java))
            } else if (!prefs.reflectionSetupDone) {
                showReflectionSetupDialog(isInitialSetup = true)
            }
        }
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

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher()) return@observe
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
            else
                resetLauncherViaFakeActivity()
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK)
                    prefs.lockModeOn = true
            }

            Constants.REQUEST_CODE_LAUNCHER_SELECTOR -> {
                if (resultCode == Activity.RESULT_OK)
                    resetLauncherViaFakeActivity()
            }
        }
    }
}