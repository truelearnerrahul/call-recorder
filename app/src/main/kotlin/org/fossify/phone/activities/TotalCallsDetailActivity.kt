package org.fossify.phone.activities

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import org.fossify.commons.extensions.*
import org.fossify.phone.R
import org.fossify.phone.adapters.CallStatsAdapter
import org.fossify.phone.adapters.RecentsAdapter
import org.fossify.phone.databinding.ActivityTotalCallsDetailBinding
import android.app.DatePickerDialog
import android.view.View
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.config
import org.fossify.phone.fragments.AnalyticsFragment.PeriodFilter
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.models.CallStats
import org.fossify.phone.models.RecentCall
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TotalCallsDetailActivity : SimpleActivity() {
    private lateinit var binding: ActivityTotalCallsDetailBinding
    private val recentsHelper = RecentsHelper(this)
    private var allCalls: List<RecentCall> = emptyList()
    private lateinit var callStatsAdapter: CallStatsAdapter
    private var currentFilterType: FilterType = FilterType.TODAY
    private var customStartDate: Long = 0
    private var customEndDate: Long = 0

    private enum class FilterType {
        TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, CUSTOM
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivityTotalCallsDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar(binding.toolbar)
        setupNavigationIcon()
        setupAdapters()
        setupPeriodDropdown()
        loadCalls()
    }

    private fun setupPeriodDropdown() {
        val items = listOf(
            getString(R.string.period_today),
            getString(R.string.period_yesterday),
            getString(R.string.period_week),
            getString(R.string.period_month),
            getString(R.string.period_year),
            getString(R.string.period_customer)
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        binding.periodDropdown.setAdapter(adapter)
        binding.periodDropdown.threshold = 0

        binding.periodDropdown.setOnClickListener {
            binding.periodDropdown.showDropDown()
        }

        binding.periodDropdown.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> setFilter(FilterType.TODAY)
                1 -> setFilter(FilterType.YESTERDAY)
                2 -> setFilter(FilterType.THIS_WEEK)
                3 -> setFilter(FilterType.THIS_MONTH)
                4 -> setFilter(FilterType.THIS_MONTH) // Year filter can reuse month logic or add new FilterType
                5 -> showCustomDateRangePicker()
            }
        }

        // Default
        binding.periodDropdown.setText(items.first(), false)
    }


    private fun setupNavigationIcon() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupAdapters() {
        callStatsAdapter = CallStatsAdapter { seconds, showSeconds -> formatDuration(seconds, showSeconds) }
        binding.callStatsRecyclerView.adapter = callStatsAdapter

        binding.callsRecyclerView.apply {
            adapter = RecentsAdapter(
                items = emptyList(),
                onItemClick = { /* Handle item click if needed */ }
            )
            setHasFixedSize(true)
        }
    }

    private fun loadCalls() {
        binding.loadingIndicator.beVisible()
        binding.emptyState.beInvisible()

        // Initialize dates for filter if not set
        if (customStartDate == 0L || customEndDate == 0L) {
            val calendar = Calendar.getInstance()
            customEndDate = calendar.timeInMillis
            
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            customStartDate = calendar.timeInMillis
        }

        recentsHelper.getRecentCalls(previousRecents = emptyList(), queryLimit = Int.MAX_VALUE) { calls ->
            runOnUiThread {
                allCalls = filterCallsByDate(calls)
                updateUI()
                binding.loadingIndicator.beInvisible()
                binding.emptyState.beVisibleIf(allCalls.isEmpty())

                // Update toolbar subtitle with filter info
                updateToolbarSubtitle()
            }
        }
    }

    private fun updateToolbarSubtitle() {
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val subtitle = when (currentFilterType) {
            FilterType.TODAY -> getString(R.string.period_today)
            FilterType.YESTERDAY -> getString(R.string.period_yesterday)
            FilterType.THIS_WEEK -> getString(R.string.period_week)
            FilterType.THIS_MONTH -> getString(R.string.period_month)
            FilterType.CUSTOM -> {
                val startDate = dateFormat.format(customStartDate)
                val endDate = dateFormat.format(customEndDate)
                "$startDate - $endDate"
            }

            else -> getString(R.string.period_today) // This should never happen
        }
        supportActionBar?.subtitle = subtitle
    }

    private fun updateUI() {
        updateSummaryStatistics()
        updateCallStats()
        setupPieChart()

        // Update the main calls list
        (binding.callsRecyclerView.adapter as? RecentsAdapter)?.updateItems(allCalls)
    }

    private fun updateSummaryStatistics() {
        if (allCalls.isEmpty()) return

        val totalCalls = allCalls.size
        val totalDuration = allCalls.sumOf { it.duration }

        binding.totalCallsCount.text = totalCalls.toString()
        binding.totalDurationText.text = formatDuration(totalDuration, false)
    }

    private fun updateCallStats() {
        if (allCalls.isEmpty()) return

        val incomingCalls = allCalls.filter { it.type == 1 } // INCOMING
        val outgoingCalls = allCalls.filter { it.type == 2 } // OUTGOING
        val missedCalls = allCalls.filter { it.type == 3 } // MISSED
        val rejectedCalls = allCalls.filter { it.type == 5 } // REJECTED

        val callStats = listOf(
            CallStats(
                callType = 1, // INCOMING
                callTypeLabel = getString(R.string.incoming_calls),
                callCount = incomingCalls.size,
                totalDuration = incomingCalls.sumOf { it.duration },
                color = getColor(R.color.call_type_incoming)
            ),
            CallStats(
                callType = 2, // OUTGOING
                callTypeLabel = getString(R.string.outgoing_calls),
                callCount = outgoingCalls.size,
                totalDuration = outgoingCalls.sumOf { it.duration },
                color = getColor(R.color.call_type_outgoing)
            ),
            CallStats(
                callType = 3, // MISSED
                callTypeLabel = getString(R.string.missed_calls),
                callCount = missedCalls.size,
                totalDuration = missedCalls.sumOf { it.duration },
                color = getColor(R.color.call_type_missed)
            ),
            CallStats(
                callType = 5, // REJECTED
                callTypeLabel = getString(R.string.rejected_calls),
                callCount = rejectedCalls.size,
                totalDuration = rejectedCalls.sumOf { it.duration },
                color = getColor(R.color.call_type_rejected)
            )
        )

        callStatsAdapter.submitList(callStats)
    }

    private fun setupPieChart() {
        if (allCalls.isEmpty()) {
            binding.callDistributionChart.visibility = android.view.View.GONE
            return
        }

        binding.callDistributionChart.apply {
            visibility = android.view.View.VISIBLE
            setUsePercentValues(true)
            description.isEnabled = false
            setExtraOffsets(5f, 10f, 5f, 5f)
            dragDecelerationFrictionCoef = 0.95f
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            animateY(1400, Easing.EaseInOutQuad)
            legend.isEnabled = false
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(12f)

            val entries = mutableListOf<PieEntry>()
            val colors = mutableListOf<Int>()

            val callStats = callStatsAdapter.currentList
            callStats.forEach { stats ->
                if (stats.callCount > 0) {
                    entries.add(PieEntry(stats.callCount.toFloat(), stats.callTypeLabel))
                    colors.add(stats.color)
                }
            }

            if (entries.isEmpty()) {
                visibility = android.view.View.GONE
                return
            }

            val dataSet = PieDataSet(entries, "").apply {
                sliceSpace = 3f
                selectionShift = 5f
                this.colors = colors
            }

            val data = PieData(dataSet).apply {
                setValueFormatter(PercentFormatter(binding.callDistributionChart))
                setValueTextSize(11f)
                setValueTextColor(Color.WHITE)
            }

            this.data = data
            highlightValues(null)
            invalidate()
        }
    }

    private fun formatDuration(seconds: Int, showSeconds: Boolean = true): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> getString(R.string.duration_hours_minutes, hours, minutes)
            minutes > 0 -> getString(R.string.duration_minutes_seconds, minutes, secs)
            else -> getString(R.string.duration_seconds, secs)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_filter -> {
                val view = findViewById<View>(R.id.action_filter)
                showFilterMenu(view)
                return true
            }
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_total_calls_detail, menu)
        return true
    }

    private fun showFilterMenu(anchor: View) {
        val popup = PopupMenu(this, binding.toolbar.findViewById(R.id.action_filter))
        popup.menuInflater.inflate(R.menu.menu_filter_options, popup.menu)

        when (currentFilterType) {
            FilterType.TODAY -> popup.menu.findItem(R.id.filter_today).isChecked = true
            FilterType.YESTERDAY -> popup.menu.findItem(R.id.filter_yesterday).isChecked = true
            FilterType.THIS_WEEK -> popup.menu.findItem(R.id.filter_this_week).isChecked = true
            FilterType.THIS_MONTH -> popup.menu.findItem(R.id.filter_this_month).isChecked = true
            FilterType.CUSTOM -> popup.menu.findItem(R.id.filter_custom).isChecked = true
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.filter_today -> setFilter(FilterType.TODAY)
                R.id.filter_yesterday -> setFilter(FilterType.YESTERDAY)
                R.id.filter_this_week -> setFilter(FilterType.THIS_WEEK)
                R.id.filter_this_month -> setFilter(FilterType.THIS_MONTH)
                R.id.filter_custom -> showCustomDateRangePicker()
            }
            true
        }
        popup.show()
    }

    private fun setFilter(filterType: FilterType) {
        currentFilterType = filterType
        
        // Update the date range based on the selected filter
        val calendar = Calendar.getInstance()
        
        when (filterType) {
            FilterType.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                customStartDate = calendar.timeInMillis
                customEndDate = System.currentTimeMillis()
            }
            FilterType.YESTERDAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                customStartDate = calendar.timeInMillis
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                customEndDate = calendar.timeInMillis
            }
            FilterType.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                customStartDate = calendar.timeInMillis
                customEndDate = System.currentTimeMillis()
            }
            FilterType.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                customStartDate = calendar.timeInMillis
                customEndDate = System.currentTimeMillis()
            }
            FilterType.CUSTOM -> {
                // Custom dates are set in the date picker dialogs
            }
        }
        
        loadCalls()
    }

    private fun showCustomDateRangePicker() {
        val calendar = Calendar.getInstance()
        val datePickerStart = DatePickerDialog(
            this,
            { _, year, month, day ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                customStartDate = cal.timeInMillis
                showEndDatePicker()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerStart.setTitle("Select Start Date")
        datePickerStart.show()
    }

    private fun showEndDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerEnd = DatePickerDialog(
            this,
            { _, year, month, day ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, day, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                customEndDate = cal.timeInMillis
                currentFilterType = FilterType.CUSTOM
                loadCalls()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerEnd.setTitle("Select End Date")
        datePickerEnd.show()
    }

    private fun filterCallsByDate(calls: List<RecentCall>): List<RecentCall> {
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()

        return when (currentFilterType) {
            FilterType.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis
                calls.filter { it.startTS in startOfDay..now }
            }

            FilterType.YESTERDAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfYesterday = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endOfYesterday = calendar.timeInMillis
                
                calls.filter { it.startTS in startOfYesterday..endOfYesterday }
            }

            FilterType.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfWeek = calendar.timeInMillis
                calls.filter { it.startTS in startOfWeek..now }
            }

            FilterType.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis
                calls.filter { it.startTS in startOfMonth..now }
            }
            FilterType.CUSTOM -> {
                calls.filter { it.startTS in customStartDate..customEndDate }
            }
        }
    }
}
