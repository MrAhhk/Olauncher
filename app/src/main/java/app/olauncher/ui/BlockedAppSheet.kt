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
import app.olauncher.R
import app.olauncher.reflection.ReflectionConstants

/** Centered popup card for blocked app (same style as ReflectionSheet). */
class BlockedAppSheet : DialogFragment() {

    companion object {
        private const val ARG_PACKAGE = "package"

        fun newInstance(packageName: String): BlockedAppSheet =
            BlockedAppSheet().apply {
                arguments = Bundle().apply { putString(ARG_PACKAGE, packageName) }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.ReflectionSheetTheme)
        isCancelable = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.dialog_blocked_app, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnOk = view.findViewById<TextView>(R.id.btnOk)

        val packageName = arguments?.getString(ARG_PACKAGE).orEmpty()
        val appName = try {
            requireContext().packageManager.getApplicationLabel(
                requireContext().packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName
        }
        tvMessage.text = "$appName — come back tomorrow"

        btnOk.setOnClickListener { dismiss() }
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
