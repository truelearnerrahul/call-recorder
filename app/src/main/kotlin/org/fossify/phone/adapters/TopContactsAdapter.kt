package org.fossify.phone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.fossify.phone.R

class TopContactsAdapter(
    private var contacts: MutableList<Pair<String, Int>>,
    private val onContactClick: (Pair<String, Int>) -> Unit
) : RecyclerView.Adapter<TopContactsAdapter.ViewHolder>() {

    fun updateItems(newItems: List<Pair<String, Int>>) {
        contacts.clear()
        contacts.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_contact_duration, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact, onContactClick)
    }

    override fun getItemCount() = contacts.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val phoneNumberText: TextView = itemView.findViewById(R.id.contact_phone_number)
        private val durationText: TextView = itemView.findViewById(R.id.contact_duration)
        private val rankText: TextView = itemView.findViewById(R.id.contact_rank)

        fun bind(contact: Pair<String, Int>, onContactClick: (Pair<String, Int>) -> Unit) {
            val (phoneNumber, duration) = contact

            rankText.text = (adapterPosition + 1).toString()
            phoneNumberText.text = phoneNumber
            durationText.text = formatDuration(duration)

            itemView.setOnClickListener {
                onContactClick(contact)
            }
        }

        private fun formatDuration(totalSeconds: Int): String {
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "${totalSeconds}s"
            }
        }
    }
}
