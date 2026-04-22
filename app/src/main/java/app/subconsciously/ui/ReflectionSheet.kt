package app.subconsciously.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import app.subconsciously.MainViewModel
import app.subconsciously.R
import app.subconsciously.data.AppModel
import app.subconsciously.data.Prefs
import app.subconsciously.helper.PromptRepository
import app.subconsciously.helper.RadarEngine
import app.subconsciously.helper.ReflectionBackgroundManager
import app.subconsciously.reflection.ReflectionConstants
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val tvTagPrompt = view.findViewById<TextView>(R.id.tvTagPrompt)
        val btnOpenAnyway = view.findViewById<TextView>(R.id.btnOpenAnyway)
        val btnContinueLater = view.findViewById<TextView>(R.id.btnContinueLater)
        val progressBar = view.findViewById<ProgressBar>(R.id.reflectionProgress)
        val card = view.findViewById<FrameLayout>(R.id.reflectionCard)
        val bgImage = view.findViewById<ImageView>(R.id.reflectionBgImage)
        val bgScrim = view.findViewById<View>(R.id.reflectionBgScrim)

        // Clip the card's children (image + scrim) to the rounded-rect card background.
        val cornerRadiusPx = resources.getDimension(R.dimen.reflection_dialog_corner_radius)
        card.clipToOutline = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, cornerRadiusPx)
            }
        }

        loadBackgroundImage(bgImage, bgScrim)

        val prefs = Prefs(requireContext())
        tvPrompt.text = "\"${PromptRepository.getRandomPrompt()}\""

        btnOpenAnyway.isEnabled = false
        btnOpenAnyway.alpha = ReflectionConstants.DISABLED_CONTROL_ALPHA
        btnContinueLater.isEnabled = false
        btnContinueLater.alpha = ReflectionConstants.DISABLED_CONTROL_ALPHA

        var tagJob: Job? = null

        fun showTagPrompt(text: String) {
            tvTagPrompt.text = "\"$text\""
            tvTagPrompt.alpha = 0f
            tvTagPrompt.visibility = View.VISIBLE
            tvTagPrompt.animate().alpha(1f).setDuration(500).withEndAction {
                tvTagPrompt.postDelayed({
                    tvTagPrompt.animate().alpha(0f).setDuration(500).withEndAction {
                        tvTagPrompt.visibility = View.INVISIBLE
                    }.start()
                }, 2000)
            }.start()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val delayMs = viewModel.currentDelayMs()
            Log.d("BURST", "ReflectionSheet delay starting: ${delayMs}ms")
            val tickMs = 32L
            val totalTicks = delayMs / tickMs
            var tick = 0L
            while (isActive && tick < totalTicks) {
                progressBar.progress = ((tick * 1000L) / totalTicks).toInt()
                delay(tickMs)
                tick++
            }
            progressBar.progress = 1000
            // User sat through the full pause — that's awareness, regardless of what they press next
            prefs.addRadarHesitation(RadarEngine.todayKey())
            Log.d("BURST", "ReflectionSheet delay done — buttons enabled")
            btnOpenAnyway.isEnabled = true
            btnOpenAnyway.alpha = 1.0f
            btnContinueLater.isEnabled = true
            btnContinueLater.alpha = 1.0f

            // TAG: user sitting on screen 4s after countdown
            tagJob = launch {
                delay(4000)
                if (isActive) showTagPrompt(PromptRepository.getTagPrompt())
            }
        }

        btnOpenAnyway.setOnClickListener {
            tagJob?.cancel()
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
            tagJob?.cancel()
            btnContinueLater.isEnabled = false
            btnOpenAnyway.isEnabled = false

            prefs.pauseCount = prefs.pauseCount + 1
            val dateKey = RadarEngine.todayKey()
            prefs.addRadarStop(dateKey)
            prefs.addRadarIntent(dateKey)
            viewModel.pendingApp = null

            // Grab root before dismiss detaches the fragment
            val root = requireActivity().window.decorView as ViewGroup
            val winText = "\"${PromptRepository.getWinPrompt()}\""
            dismiss()

            val winView = TextView(root.context).apply {
                text = winText
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                setTextColor(0xF2FFFFFFu.toInt())
                gravity = Gravity.CENTER
                alpha = 0f
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0x99000000u.toInt())
            }
            root.addView(winView)
            winView.animate().alpha(1f).setDuration(400).withEndAction {
                winView.postDelayed({
                    winView.animate().alpha(0f).setDuration(400).withEndAction {
                        root.removeView(winView)
                    }.start()
                }, 2200)
            }.start()
        }
    }

    /**
     * Async load a random cached background bitmap, apply blur (RenderEffect on API 31+),
     * fade in image + scrim. Triggers a background refill for next time.
     */
    private fun loadBackgroundImage(bgImage: ImageView, bgScrim: View) {
        val appCtx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val file = ReflectionBackgroundManager.pickRandomFile(appCtx) ?: return@withContext null
                try {
                    BitmapFactory.decodeFile(file.absolutePath)
                } catch (t: Throwable) {
                    Log.w("ReflectionBg", "decode cache error: ${t.message}")
                    null
                }
            }
            if (bitmap != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.INITIALIZED
                )) {
                bgImage.setImageBitmap(bitmap)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bgImage.setRenderEffect(
                        RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP)
                    )
                }
                bgImage.animate().alpha(1f).setDuration(250).start()
                bgScrim.animate().alpha(1f).setDuration(250).start()
            }
            // Refill cache for next overlay.
            ReflectionBackgroundManager.ensureCached(appCtx)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val widthPx =
                (resources.displayMetrics.widthPixels * ReflectionConstants.DIALOG_WIDTH_FRACTION_MAIN).toInt()
            val heightPx =
                (resources.displayMetrics.heightPixels * 0.5f).toInt()
            w.setLayout(widthPx, heightPx)
            w.setGravity(Gravity.CENTER)
        }
    }
}
