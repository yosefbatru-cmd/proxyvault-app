package com.spiritdev.proxyvault.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.spiritdev.proxyvault.R
import com.spiritdev.proxyvault.databinding.ItemProxyBinding
import com.spiritdev.proxyvault.model.ProxyItem

class ProxyAdapter(
    private val onCopy: (ProxyItem) -> Unit,
    private val onTest: (ProxyItem) -> Unit
) : RecyclerView.Adapter<ProxyAdapter.VH>() {

    private val items = mutableListOf<ProxyItem>()

    fun submit(list: List<ProxyItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].address == list[n].address
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == list[n]
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProxyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemProxyBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ProxyItem) {
            binding.tvAddress.text = item.address
            binding.tvMeta.text = "${item.protocol.uppercase()} · ${item.displaySpeed} · ${item.source}"
            binding.tvCountry.text = item.countryCode.ifBlank { "XX" }
            val speedColor = when {
                item.speedMs < 0 -> ContextCompat.getColor(binding.root.context, R.color.text_secondary)
                item.speedMs < 300 -> ContextCompat.getColor(binding.root.context, R.color.speed_fast)
                item.speedMs < 800 -> ContextCompat.getColor(binding.root.context, R.color.speed_mid)
                else -> ContextCompat.getColor(binding.root.context, R.color.speed_slow)
            }
            binding.speedBar.setBackgroundColor(speedColor)
            binding.root.setOnClickListener { onCopy(item) }
            binding.btnCopy.setOnClickListener { onCopy(item) }
            binding.btnTest.setOnClickListener { onTest(item) }
            binding.root.setOnLongClickListener { onTest(item); true }
        }
    }
}
