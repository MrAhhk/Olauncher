package app.olauncher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.data.DistractionList
import app.olauncher.data.Prefs
import app.olauncher.helper.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object OnboardingFlow {

    private val REASONS = arrayOf(
        "I spend too much time on my phone",
        "I want to be more intentional",
        "I need to focus",
        "I'm addicted to certain apps",
        "I want to change",
        "Just exploring"
    )

    fun start(activity: AppCompatActivity, prefs: Prefs) {
        showDialog1(activity, prefs)
    }

    // ── Dialog 1 ─────────────────────────────────────────────────────────────

    private fun showDialog1(activity: AppCompatActivity, prefs: Prefs) {
        val radioGroup = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
            val pad = 16.dpToPx()
            setPadding(pad, pad / 2, pad, pad / 2)
            REASONS.forEachIndexed { i, text ->
                addView(RadioButton(activity).apply {
                    id = i
                    this.text = text
                    val v = 6.dpToPx()
                    setPadding(8.dpToPx(), v, 8.dpToPx(), v)
                })
            }
        }

        val dialog = AlertDialog.Builder(activity, R.style.ReflectionSetupDialog)
            .setTitle("Why are you here?")
            .setView(radioGroup)
            .setCancelable(false)
            .setPositiveButton("CONTINUE", null)
            .create()

        dialog.show()

        val continueBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        continueBtn.isEnabled = false

        var selectedIndex = -1
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedIndex = checkedId
            continueBtn.isEnabled = true
        }

        continueBtn.setOnClickListener {
            prefs.onboardingReason = REASONS[selectedIndex]
            dialog.dismiss()
            showDialog2(activity, prefs)
        }
    }

    // ── Dialog 2 ─────────────────────────────────────────────────────────────

    private fun showDialog2(activity: AppCompatActivity, prefs: Prefs) {
        activity.lifecycleScope.launch {
            val distractionApps = withContext(Dispatchers.IO) {
                val dl = DistractionList(activity)
                activity.packageManager
                    .getInstalledApplications(0)
                    .filter { dl.isDistraction(it.packageName) }
                    .map { activity.packageManager.getApplicationLabel(it).toString() to it.packageName }
                    .sortedBy { it.first.lowercase() }
            }

            if (distractionApps.isEmpty()) {
                showDialog3(activity, prefs)
                return@launch
            }

            val pad = 16.dpToPx()
            val itemV = 6.dpToPx()

            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, itemV, pad, itemV)
            }
            val checkBoxes = distractionApps.map { (label, pkg) ->
                val cb = CheckBox(activity).apply {
                    text = label
                    isChecked = true
                    setPadding(4.dpToPx(), itemV, 4.dpToPx(), itemV)
                }
                container.addView(cb)
                cb to pkg
            }

            val scrollView = ScrollView(activity).also { it.addView(container) }

            AlertDialog.Builder(activity, R.style.ReflectionSetupDialog)
                .setTitle("These apps will trigger a reflection pause.")
                .setView(scrollView)
                .setCancelable(false)
                .setPositiveButton("CONFIRM") { _, _ ->
                    val unchecked = checkBoxes.filter { (cb, _) -> !cb.isChecked }.map { it.second }.toSet()
                    if (unchecked.isNotEmpty()) {
                        val sp = activity.getSharedPreferences("app.olauncher", Context.MODE_PRIVATE)
                        val existing = sp.getStringSet("distraction_whitelist", emptySet()) ?: emptySet()
                        sp.edit { putStringSet("distraction_whitelist", existing + unchecked) }
                    }
                    showDialog3(activity, prefs)
                }
                .setNegativeButton("SKIP") { _, _ -> showDialog3(activity, prefs) }
                .show()
        }
    }

    // ── Dialog 3 ─────────────────────────────────────────────────────────────

    private fun showDialog3(activity: AppCompatActivity, prefs: Prefs) {
        AlertDialog.Builder(activity, R.style.ReflectionSetupDialog)
            .setTitle("Optional: Usage Access")
            .setMessage("Allows the app to adjust reflection frequency based on your screen time. Not required.")
            .setCancelable(false)
            .setPositiveButton("GRANT ACCESS") { _, _ ->
                prefs.onboardingComplete = true
                prefs.reflectionSetupDone = true
                activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("SKIP") { _, _ ->
                prefs.onboardingComplete = true
                prefs.reflectionSetupDone = true
            }
            .show()
    }
}
