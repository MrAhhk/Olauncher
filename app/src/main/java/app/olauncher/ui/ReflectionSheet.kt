package app.olauncher.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import app.olauncher.helper.PromptRepository
import app.olauncher.reflection.ReflectionConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Centered reflection prompt (same card style as settings); not a bottom sheet. */
class ReflectionSheet : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()

    companion object {
        fun newInstance(): ReflectionSheet = ReflectionSheet()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.ReflectionSheetTheme)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_reflection_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvPrompt = view.findViewById<TextView>(R.id.tvPrompt)
        val btnOpenAnyway = view.findViewById<TextView>(R.id.btnOpenAnyway)
        val btnContinueLater = view.findViewById<TextView>(R.id.btnContinueLater)

        val prefs = Prefs(requireContext())
        tvPrompt.text = PromptRepository.getRandomPrompt()

        btnOpenAnyway.isEnabled = false
        btnOpenAnyway.alpha = ReflectionConstants.DISABLED_CONTROL_ALPHA
        btnContinueLater.isEnabled = false
        btnContinueLater.alpha = ReflectionConstants.DISABLED_CONTROL_ALPHA

        viewLifecycleOwner.lifecycleScope.launch {
            delay(ReflectionConstants.PROMPT_BUTTON_DELAY_MS)
            btnOpenAnyway.isEnabled = true
            btnOpenAnyway.alpha = 1.0f
            btnContinueLater.isEnabled = true
            btnContinueLater.alpha = 1.0f
        }

        btnOpenAnyway.setOnClickListener {
            val app = viewModel.pendingApp
            if (app == null) {
                dismiss()
                return@setOnClickListener
            }
            dismiss()
            when (app) {
                is AppModel.App -> viewModel.launchApp(
                    app.appPackage,
                    app.activityClassName,
                    app.user,
                )
                is AppModel.PinnedShortcut -> viewModel.launchShortcut(app)
            }
            viewModel.pendingApp = null
        }

        btnContinueLater.setOnClickListener {
            prefs.pauseCount = prefs.pauseCount + 1
            viewModel.pendingApp = null
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val widthPx =
                (resources.displayMetrics.widthPixels * ReflectionConstants.DIALOG_WIDTH_FRACTION_MAIN).toInt()
            w.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.CENTER)
        }
    }
}
