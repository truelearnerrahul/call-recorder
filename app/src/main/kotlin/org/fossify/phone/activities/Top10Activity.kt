package org.fossify.phone.activities

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.phone.R
import org.fossify.phone.adapters.Top10Adapter
import org.fossify.phone.databinding.ActivityTop10Binding
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.models.RecentCall
import java.util.concurrent.TimeUnit

class Top10Activity : AppCompatActivity() {
    private val recentsHelper = RecentsHelper(this)
    private var allCalls: List<RecentCall> = emptyList()
    private lateinit var binding: ActivityTop10Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTop10Binding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupViews()
        loadData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        binding.recyclerViewTop10.layoutManager = LinearLayoutManager(this)

        // Initially show loader, hide other views
        binding.loadingIndicator.beVisible()
        binding.recyclerViewTop10.beInvisible()
        binding.emptyState.beInvisible()
    }

    private var currentType = ""

    private fun loadData() {
        currentType = intent.getStringExtra("type") ?: "frequent"
        updateTitle()

        recentsHelper.getRecentCalls(previousRecents = emptyList(), queryLimit = Int.MAX_VALUE) { calls ->
            runOnUiThread {
                allCalls = calls

                // Generate data AFTER calls are loaded
                val data = when (currentType) {
                    "frequent" -> getTop10FrequentTalked(this, allCalls)
                    "duration" -> getTop10CallDuration(this, allCalls)
                    else -> emptyList()
                }

                updateUI(data, currentType == "duration")
            }
        }
    }

    private fun updateUI(data: List<Pair<String, Int>>, isDurationType: Boolean) {
        // Hide loader
        binding.loadingIndicator.beInvisible()

        if (data.isEmpty()) {
            // Show empty state
            binding.emptyState.beVisible()
            binding.recyclerViewTop10.beInvisible()
        } else {
            // Show data
            binding.emptyState.beInvisible()
            binding.recyclerViewTop10.beVisible()
            binding.recyclerViewTop10.adapter = Top10Adapter(data, isDurationType)
        }
    }

    fun getTop10FrequentTalked(context: Context, calls: List<RecentCall>): List<Pair<String, Int>> {
        return calls.filter { it.duration > 0 } // only answered
            .groupingBy { it.phoneNumber }
            .eachCount() // map: number -> call count
            .entries
            .sortedByDescending { it.value } // sort by count
            .take(10)
            .map { entry ->
                val nameOrNumber = recentsHelper.getContactName(context, entry.key)
                nameOrNumber to entry.value
            }
    }

    private fun updateTitle() {
        val title = when (currentType) {
            "frequent" -> getString(R.string.top_10_frequent_calls)
            "duration" -> getString(R.string.top_10_call_duration)
            else -> getString(R.string.top_10_calls)
        }
        binding.toolbar.title = title
    }

    private fun formatDuration(seconds: Int): String {
        val hours = TimeUnit.SECONDS.toHours(seconds.toLong())
        val minutes = TimeUnit.SECONDS.toMinutes(seconds.toLong()) - TimeUnit.HOURS.toMinutes(hours)
        return when {
            hours > 0 -> String.format("%d hr %d min", hours, minutes)
            else -> String.format("%d min", minutes)
        }
    }

    fun getTop10CallDuration(context: Context, calls: List<RecentCall>): List<Pair<String, Int>> {
        return calls.filter { it.duration > 0 }
            .groupBy { it.phoneNumber }
            .mapValues { entry -> entry.value.sumOf { it.duration } } // number -> total duration in seconds
            .entries
            .sortedByDescending { it.value } // sort by duration
            .take(10)
            .map { entry ->
                val nameOrNumber = recentsHelper.getContactName(context, entry.key)
                nameOrNumber to entry.value
            }
    }
}
