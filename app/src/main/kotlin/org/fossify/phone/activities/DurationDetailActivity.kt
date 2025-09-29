package org.fossify.phone.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import org.fossify.commons.extensions.*
import org.fossify.phone.R
import org.fossify.phone.adapters.TopContactsAdapter
import org.fossify.phone.databinding.ActivityDurationDetailBinding
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.helpers.shareUri
import org.fossify.phone.models.RecentCall
import java.util.*
import kotlin.collections.HashMap

class DurationDetailActivity : SimpleActivity() {
    private lateinit var binding: ActivityDurationDetailBinding
    private val recentsHelper = RecentsHelper(this)
    private var allCalls: List<RecentCall> = emptyList()
    private var topContactsAdapter: TopContactsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivityDurationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar(binding.toolbar)
        setupNavigationIcon()

        setupChart()
        loadCalls()
    }

    private fun setupChart() {
        val chart: BarChart = binding.durationChart
        chart.description.isEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.granularity = 1f
        chart.axisLeft.textColor = getProperTextColor()
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            labelCount = 1
            textColor = getProperTextColor()
        }
        chart.legend.apply {
            isEnabled = true
            verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
            horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.LEFT
            orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            textColor = getProperTextColor()
        }
    }

    private fun loadCalls() {
        binding.loadingIndicator.beVisible()

        recentsHelper.getRecentCalls(previousRecents = emptyList(), queryLimit = Int.MAX_VALUE) { calls ->
            runOnUiThread {
                allCalls = calls
                updateUI()
                binding.loadingIndicator.beGone()
            }
        }
    }

    private fun updateUI() {
        val calls = allCalls.filter { it.duration > 0 }

        if (calls.isEmpty()) {
            showEmptyState()
            return
        }

        // Update summary cards
        updateSummaryCards(calls)

        // Update chart
        updateDurationChart(calls)

        // Update top contacts by duration
        updateTopContactsByDuration(calls)
    }

    private fun updateSummaryCards(calls: List<RecentCall>) {
        val totalDuration = calls.sumOf { it.duration }
        val averageDuration = if (calls.isNotEmpty()) totalDuration / calls.size else 0
        val longestCall = calls.maxOfOrNull { it.duration } ?: 0

        binding.totalDurationText.text = formatDuration(totalDuration)
        binding.averageDurationText.text = formatDuration(averageDuration)
        binding.longestCallText.text = formatDuration(longestCall)
        binding.totalCallsWithDuration.text = calls.size.toString()
    }

    private fun updateDurationChart(calls: List<RecentCall>) {
        // Group calls by duration ranges
        val durationRanges = mapOf(
            "0-1m" to calls.count { it.duration <= 60 },
            "1-5m" to calls.count { it.duration in 61..300 },
            "5-15m" to calls.count { it.duration in 301..900 },
            "15-30m" to calls.count { it.duration in 901..1800 },
            "30m+" to calls.count { it.duration > 1800 }
        )

        val entries = durationRanges.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Call Duration Distribution").apply {
            color = getProperPrimaryColor()
            valueTextColor = getProperTextColor()
        }

        val barData = BarData(dataSet).apply {
            setValueTextColor(getProperTextColor())
            barWidth = 0.6f
        }

        val valueFormatter = object : ValueFormatter() {
            override fun getBarLabel(barEntry: BarEntry?): String {
                return barEntry?.y?.toInt()?.toString() ?: "0"
            }
        }

        barData.setValueFormatter(valueFormatter)
        binding.durationChart.xAxis.valueFormatter = IndexAxisValueFormatter(durationRanges.keys.toList())
        binding.durationChart.data = barData
        binding.durationChart.invalidate()
        binding.durationChart.animateY(600)
    }

    private fun updateTopContactsByDuration(calls: List<RecentCall>) {
        val durationByNumber = HashMap<String, Int>()
        calls.forEach { call ->
            durationByNumber[call.phoneNumber] = (durationByNumber[call.phoneNumber] ?: 0) + call.duration
        }

        val topContacts = durationByNumber.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value } // Convert Map.Entry to Pair

        if (binding.topContactsRecyclerView.adapter == null) {
            binding.topContactsRecyclerView.layoutManager = LinearLayoutManager(this)
            val adapter = TopContactsAdapter(topContacts.toMutableList()) { contact ->
                // Handle contact click
            }
            binding.topContactsRecyclerView.adapter = adapter
        } else {
            val adapter = binding.topContactsRecyclerView.adapter as TopContactsAdapter
            adapter.updateItems(topContacts)
        }
    }

    private fun showEmptyState() {
        binding.emptyState.beVisible()
        binding.contentContainer.beGone()
    }

    private fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_duration_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> {
                exportDurationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportDurationData() {
        // Export duration analysis data
        val calls = allCalls.filter { it.duration > 0 }

        if (calls.isEmpty()) {
            toast(R.string.no_data_to_export)
            return
        }

        // Create CSV or other export format
        val csvContent = buildString {
            append("Phone Number,Duration (seconds),Date,Type\n")
            calls.forEach { call ->
                append("${call.phoneNumber},${call.duration},${call.startTS},${call.type}\n")
            }
        }

        // Share or save the file
        shareFile(csvContent, "call_duration_analysis.csv")
    }

    private fun shareFile(content: String, fileName: String) {
        try {
            val file = createTempFile(fileName.replace(".csv", ""), ".csv")
            file.writeText(content)
            shareUri(file, "text/csv")
        } catch (e: Exception) {
            toast(R.string.unknown_error_occurred)
        }
    }

    private fun setupNavigationIcon() {
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
}
