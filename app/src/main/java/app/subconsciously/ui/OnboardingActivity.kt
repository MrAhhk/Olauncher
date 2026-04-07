package app.subconsciously.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.subconsciously.R
import app.subconsciously.data.DistractionList
import app.subconsciously.data.Prefs
import app.subconsciously.databinding.ActivityOnboardingBinding
import app.subconsciously.helper.dpToPx
import app.subconsciously.helper.isDeviceLocationEnabled
import app.subconsciously.helper.openDeviceLocationSettingsOrPanel
import app.subconsciously.reflection.ReflectionAlphabetStrip
import app.subconsciously.reflection.ReflectionAppListAdapter
import app.subconsciously.reflection.ReflectionAppRow
import app.subconsciously.reflection.ReflectionSetupRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var prefs: Prefs

    private val reasons = arrayOf(
        "I spend too much time on my phone",
        "I want to be more intentional",
        "I need to focus",
        "I'm addicted to certain apps",
        "I want to change",
        "Just exploring"
    )

    private var selectedReasonIndex = -1
    private var appRows: MutableList<ReflectionAppRow>? = null
    private var awaitingAccessReturn = false

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val allowed = granted.values.any { it }
        // Post so navigation runs after the permission activity tears down; avoids no-op transitions on some devices.
        if (isDestroyed) return@registerForActivityResult
        binding.root.post {
            if (isDestroyed) return@post
            navigateTo(binding.pageLocation.root, binding.pageAccess.root)
            if (allowed && !isDeviceLocationEnabled()) {
                openDeviceLocationSettingsOrPanel()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (prefs.onboardingComplete) { setResult(RESULT_OK); finish(); return }
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

        setupReasonPage()
        setupAppsPageButtons()
        setupLocationPage()
        setupAccessPage()
    }

    override fun onResume() {
        super.onResume()
        if (awaitingAccessReturn) {
            finishOnboarding()
        }
    }

    // ── Page 1: Reason ───────────────────────────────────────────────────────

    private fun setupReasonPage() {
        val container = binding.pageReason.optionsContainer
        val btnContinue = binding.pageReason.btnContinue
        val pad = 16.dpToPx()
        val padH = 20.dpToPx()
        val marginBottom = 8.dpToPx()

        reasons.forEachIndexed { index, label ->
            val tv = TextView(this).apply {
                text = label
                setTextColor(getColor(R.color.whiteTrans90))
                textSize = 15f
                setPadding(padH, pad, padH, pad)
                setBackgroundResource(R.drawable.bg_onboarding_option)
                isClickable = true
                isFocusable = true
                val rippleAttr = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, rippleAttr, true)
                foreground = getDrawable(rippleAttr.resourceId)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = marginBottom }
            tv.layoutParams = lp

            tv.setOnClickListener {
                // Deselect previous
                if (selectedReasonIndex >= 0) {
                    (container.getChildAt(selectedReasonIndex) as? TextView)
                        ?.setBackgroundResource(R.drawable.bg_onboarding_option)
                }
                selectedReasonIndex = index
                tv.setBackgroundResource(R.drawable.bg_onboarding_option_selected)
                btnContinue.isEnabled = true
                btnContinue.alpha = 1f
            }

            container.addView(tv)
        }

        btnContinue.setOnClickListener {
            if (selectedReasonIndex >= 0) {
                prefs.onboardingReason = reasons[selectedReasonIndex]
                navigateTo(binding.pageReason.root, binding.pageApps.root)
                loadAppsAsync()
            }
        }
    }

    // ── Page 2: Apps ─────────────────────────────────────────────────────────

    private fun setupAppsPageButtons() {
        binding.pageApps.btnSkip.setOnClickListener {
            navigateToLocation(binding.pageApps.root)
        }
        // btnConfirm is wired after rows are loaded in loadAppsAsync()
    }

    private fun loadAppsAsync() {
        val distractionList = DistractionList(this)
        val hiddenSuffix = getString(R.string.reflection_list_hidden_suffix)

        lifecycleScope.launch {
            val (apps, rows) = withContext(Dispatchers.IO) {
                val a = distractionList.getAllAppsForReflectionSetup()
                val r = if (a.isEmpty()) null
                        else ReflectionSetupRows.build(distractionList, prefs, a, hiddenSuffix)
                Pair(a, r)
            }

            if (apps.isEmpty() || rows == null) {
                navigateToLocation(binding.pageApps.root)
                return@launch
            }

            appRows = rows

            val appsPage = binding.pageApps
            appsPage.loadingIndicator.visibility = View.GONE
            appsPage.listFrame.visibility = View.VISIBLE

            val adapter = ReflectionAppListAdapter(
                rows = rows,
                useUntickPauseDialog = false,
                onUntickAttempt = {},
            )
            appsPage.onboardingAppsList.layoutManager = LinearLayoutManager(this@OnboardingActivity)
            appsPage.onboardingAppsList.adapter = adapter

            ReflectionAlphabetStrip.attach(
                this@OnboardingActivity,
                appsPage.onboardingAlphabetIndex,
                appsPage.onboardingAppsList,
                rows,
                hiddenSuffix,
            )

            appsPage.btnConfirm.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    distractionList.applyReflectionSelectionBatch(rows)
                    withContext(Dispatchers.Main) {
                        navigateToLocation(appsPage.root)
                    }
                }
            }
        }
    }

    // ── Page 3: Location ─────────────────────────────────────────────────────

    private fun setupLocationPage() {
        binding.pageLocation.btnGrant.setOnClickListener {
            requestLocationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        binding.pageLocation.btnSkip.setOnClickListener {
            markLocationAsked()
            navigateTo(binding.pageLocation.root, binding.pageAccess.root)
        }
    }

    // ── Page 4: Access ───────────────────────────────────────────────────────

    private fun setupAccessPage() {
        binding.pageAccess.btnGrant.setOnClickListener {
            awaitingAccessReturn = true
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.pageAccess.btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Prefs are committed in MainActivity when it receives [RESULT_OK]. */
    private fun finishOnboarding() {
        setResult(RESULT_OK)
        finish()
    }

    private fun navigateToLocation(fromView: View) {
        markLocationAsked()
        navigateTo(fromView, binding.pageLocation.root)
    }

    private fun markLocationAsked() {
        getSharedPreferences("weather_cache", MODE_PRIVATE)
            .edit().putBoolean(HomeFragment.KEY_LOCATION_ASKED, true).apply()
    }

    private fun navigateTo(outView: View, inView: View) {
        outView.animate().alpha(0f).setDuration(220).withEndAction {
            outView.visibility = View.GONE
            outView.alpha = 1f
            inView.alpha = 0f
            inView.visibility = View.VISIBLE
            inView.animate().alpha(1f).setDuration(220).start()
        }.start()
    }
}
