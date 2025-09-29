package org.fossify.phone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.phone.databinding.ItemRecentCallBinding
import org.fossify.phone.models.RecentCall

class RecentsAdapter(
    private var items: List<RecentCall>,
    private val onItemClick: (RecentCall) -> Unit
) : RecyclerView.Adapter<RecentsAdapter.RecentViewHolder>() {

    fun updateItems(newItems: List<RecentCall>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class RecentViewHolder(val binding: ItemRecentCallBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecentCall) {
            binding.itemRecentsName.text = item.name ?: item.phoneNumber
            binding.itemRecentsDuration.text = "${item.duration}s"

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val binding = ItemRecentCallBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
