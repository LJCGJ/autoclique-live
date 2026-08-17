package com.autoclique.live.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.autoclique.live.databinding.ItemPointBinding
import com.autoclique.live.model.ClickPoint
import com.autoclique.live.util.Tempo

class PointAdapter(
    private val onToggle: (ClickPoint, Boolean) -> Unit,
    private val onEdit: (ClickPoint) -> Unit
) : RecyclerView.Adapter<PointAdapter.Holder>() {

    private var items: List<ClickPoint> = emptyList()

    fun submit(list: List<ClickPoint>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val b: ItemPointBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPointBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val p = items[position]
        val b = holder.b

        b.tvName.text = p.name
        b.tvDetails.text = buildString {
            append(Tempo.resumo(p.intervalMs))
            append("  •  x ").append(p.x).append(", y ").append(p.y)
            if (p.useColor) {
                append("  •  cor ").append(hex(p.targetColor))
                append(" (tol. ").append(p.tolerance).append("%)")
            }
        }

        b.vColor.paintSwatch(if (p.useColor) p.targetColor else null)

        b.swEnabled.setOnCheckedChangeListener(null)
        b.swEnabled.isChecked = p.enabled
        b.swEnabled.setOnCheckedChangeListener { _, checked -> onToggle(p, checked) }

        b.btnEdit.setOnClickListener { onEdit(p) }
        b.root.setOnClickListener { onEdit(p) }
    }
}
