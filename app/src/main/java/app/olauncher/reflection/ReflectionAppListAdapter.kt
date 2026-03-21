package app.olauncher.reflection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.helper.applyLockedBlurEffect
import android.widget.TextView

/**
 * Reflection setup list: locked rows (game/hidden) vs optional untick confirmation flow.
 */
internal class ReflectionAppListAdapter(
    private val rows: List<ReflectionAppRow>,
    private val useUntickPauseDialog: Boolean,
    private val onUntickAttempt: (Int) -> Unit,
) : RecyclerView.Adapter<ReflectionAppListAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reflection_app_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.label.text = row.label
        if (row.isLocked) {
            row.checked = true
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = true
            holder.checkbox.isEnabled = false
            holder.checkbox.isClickable = false
            holder.itemView.setOnClickListener(null)
            holder.itemView.applyLockedBlurEffect(true)
        } else {
            holder.checkbox.isEnabled = true
            holder.checkbox.isClickable = true
            holder.itemView.applyLockedBlurEffect(false)
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = row.checked
            if (useUntickPauseDialog) {
                fun attachCheckboxListener() {
                    holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                        if (row.isLocked) return@setOnCheckedChangeListener
                        if (row.checked && !isChecked) {
                            holder.checkbox.setOnCheckedChangeListener(null)
                            holder.checkbox.isChecked = true
                            row.checked = true
                            attachCheckboxListener()
                            val pos = holder.bindingAdapterPosition
                            if (pos != RecyclerView.NO_POSITION) {
                                onUntickAttempt(pos)
                            }
                            return@setOnCheckedChangeListener
                        }
                        row.checked = isChecked
                    }
                }
                attachCheckboxListener()
            } else {
                holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (!row.isLocked) row.checked = isChecked
                }
            }
            holder.itemView.setOnClickListener {
                holder.checkbox.toggle()
            }
        }
    }

    override fun getItemCount() = rows.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: AppCompatCheckBox = view.findViewById(R.id.reflection_row_checkbox)
        val label: TextView = view.findViewById(R.id.reflection_row_label)
    }
}
