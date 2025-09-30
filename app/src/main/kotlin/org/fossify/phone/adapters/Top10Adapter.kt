package org.fossify.phone.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.fossify.phone.R
import java.util.concurrent.TimeUnit

class Top10Adapter(
    private val items: List<Pair<String, Int>>,
    private val isDurationType: Boolean = false
) : RecyclerView.Adapter<Top10Adapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textName)
        val value: TextView = view.findViewById(R.id.textValue)
        val context: Context = view.context
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top10, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, value) = items[position]
        holder.name.text = name
        
        holder.value.text = if (isDurationType) {
            formatDuration(holder.context, value)
        } else {
            holder.context.resources.getQuantityString(
                R.plurals.call_count, 
                value,
                value
            )
        }
    }

    private fun formatDuration(context: Context, seconds: Int): String {
        val hours = TimeUnit.SECONDS.toHours(seconds.toLong())
        val minutes = TimeUnit.SECONDS.toMinutes(seconds.toLong()) - TimeUnit.HOURS.toMinutes(hours)
        
        return when {
            hours > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
            else -> context.getString(R.string.duration_minutes, minutes)
        }
    }

    override fun getItemCount() = items.size
}
