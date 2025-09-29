package org.fossify.phone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.fossify.phone.R
import org.fossify.phone.databinding.ItemCallStatsBinding
import org.fossify.phone.models.CallStats

class CallStatsAdapter(
    private val formatDuration: (Int, Boolean) -> String
) : ListAdapter<CallStats, CallStatsAdapter.ViewHolder>(CallStatsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCallStatsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCallStatsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(stats: CallStats) {
            with(binding) {
                callTypeLabel.text = stats.callTypeLabel
                callCount.text = stats.callCount.toString()
                callDuration.text = this@CallStatsAdapter.formatDuration(stats.totalDuration, false)
                
                // Set the color indicator based on call type
                callTypeLabel.setTextColor(stats.color)
            }
        }
    }

    private class CallStatsDiffCallback : DiffUtil.ItemCallback<CallStats>() {
        override fun areItemsTheSame(oldItem: CallStats, newItem: CallStats): Boolean {
            return oldItem.callType == newItem.callType
        }

        override fun areContentsTheSame(oldItem: CallStats, newItem: CallStats): Boolean {
            return oldItem == newItem
        }
    }
}
