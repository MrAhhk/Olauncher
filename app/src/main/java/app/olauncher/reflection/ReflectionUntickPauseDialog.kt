package app.olauncher.reflection

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.databinding.DialogReflectionUntickPauseBinding
import app.olauncher.helper.PromptRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When the user tries to disable reflection pause on an app, buttons stay disabled briefly
 * so the choice is deliberate (same delay as [ReflectionConstants.PROMPT_BUTTON_DELAY_MS]).
 */
internal object ReflectionUntickPauseDialog {

    private var dialogRef: AlertDialog? = null

    fun show(
        activity: AppCompatActivity,
        adapter: ReflectionAppListAdapter,
        rows: MutableList<ReflectionAppRow>,
        position: Int,
    ) {
        if (dialogRef?.isShowing == true) return
        if (position !in rows.indices) return

        val pauseBinding = DialogReflectionUntickPauseBinding.inflate(activity.layoutInflater)
        pauseBinding.reflectionUntickPauseMessage.text =
            PromptRepository.getUnblockPrompt()
        val btnYes = pauseBinding.reflectionUntickPauseYes
        val btnNo = pauseBinding.reflectionUntickPauseNo
        listOf(btnYes, btnNo).forEach { b ->
            b.isEnabled = false
            b.alpha = ReflectionConstants.DISABLED_CONTROL_ALPHA
        }

        val pauseDialog = AlertDialog.Builder(activity, R.style.ReflectionSetupDialog)
            .setView(pauseBinding.root)
            .setCancelable(false)
            .create()
        dialogRef = pauseDialog
        pauseDialog.setOnDismissListener { dialogRef = null }

        btnYes.setOnClickListener {
            pauseDialog.dismiss()
        }
        btnNo.setOnClickListener {
            rows.getOrNull(position)?.let { row ->
                if (!row.isLocked) {
                    row.checked = false
                    adapter.notifyItemChanged(position)
                }
            }
            pauseDialog.dismiss()
        }

        pauseDialog.show()
        pauseDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val widthPx =
            (activity.resources.displayMetrics.widthPixels * ReflectionConstants.DIALOG_WIDTH_FRACTION_UNTICK).toInt()
        pauseDialog.window?.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)

        activity.lifecycleScope.launch {
            delay(ReflectionConstants.PROMPT_BUTTON_DELAY_MS)
            if (!pauseDialog.isShowing) return@launch
            btnYes.isEnabled = true
            btnYes.alpha = 1f
            btnNo.isEnabled = true
            btnNo.alpha = 1f
        }
    }
}
