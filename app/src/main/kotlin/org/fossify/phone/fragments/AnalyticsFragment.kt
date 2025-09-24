package org.fossify.phone.fragments

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.AxisBase
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.FragmentAnalyticsBinding
import org.fossify.phone.extensions.areMultipleSIMsAvailable
import org.fossify.phone.extensions.getAvailableSIMCardLabels
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.models.RecentCall
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

class AnalyticsFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<MyViewPagerFragment.InnerBinding>(context, attributeSet) {

    private lateinit var binding: FragmentAnalyticsBinding
    private val recentsHelper = RecentsHelper(context)

    // filters
    private var selectedSim: SimFilter = SimFilter.SIM1
    private var selectedPeriod: PeriodFilter = PeriodFilter.TODAY
    private var customStart: Long? = null
    private var customEnd: Long? = null

    // UI state
    private var chartMode: ChartMode = ChartMode.COUNTS
    private var lastStats: Stats? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentAnalyticsBinding.bind(this)
        // we do not use RecyclerView in this fragment
        innerBinding = object : InnerBinding {
            override val fragmentList = null
            override val recentsList = null
        }
    }

    override fun setupFragment() {
        setupSimChips()
        setupPeriodDropdown()
        setupColors(activity!!.getProperTextColor(), activity!!.getProperPrimaryColor(), activity!!.getProperPrimaryColor())
        setupChartAppearance()
        setupChartModeToggle()
        setupSectionToggle()
        updateRangeCaption()
        setLoading(true)
        loadAndRender()
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        // apply colors to titles if needed
        // Using default theming; nothing special required here
    }

    override fun onSearchClosed() { /* no-op */ }

    override fun onSearchQueryChanged(text: String) { /* no-op */ }

    private fun setupSimChips() {
        val group: ChipGroup = binding.simChipGroup
        val chip1: Chip = binding.chipSim1
        val chip2: Chip = binding.chipSim2
        val chipBoth: Chip = binding.chipSimBoth

        // Default selection
        group.check(R.id.chip_sim1)
        selectedSim = SimFilter.SIM1

        // Hide SIM2 if device has only one SIM
        if (!context.areMultipleSIMsAvailable()) {
            chip2.isEnabled = false
            chip2.isClickable = false
        }

        group.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedSim = when (checkedIds.firstOrNull()) {
                R.id.chip_sim1 -> SimFilter.SIM1
                R.id.chip_sim2 -> SimFilter.SIM2
                R.id.chip_sim_both -> SimFilter.BOTH
                else -> SimFilter.SIM1
            }
            setLoading(true)
            loadAndRender()
        }

        // Optionally label chips with SIM labels
        val sims = context.getAvailableSIMCardLabels()
        sims.getOrNull(0)?.let { chip1.text = resources.getString(R.string.sim_with_label, 1, it.label) }
        sims.getOrNull(1)?.let { chip2.text = resources.getString(R.string.sim_with_label, 2, it.label) }
    }

    private fun setupSectionToggle() {
        // Default to Summary
        binding.sectionToggle.check(R.id.btn_section_summary)
        binding.sectionToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val showSummary = checkedId == R.id.btn_section_summary
            binding.summaryContainer.visibility = if (showSummary) View.VISIBLE else View.GONE
            binding.analysisContainer.visibility = if (showSummary) View.GONE else View.VISIBLE
        }
    }

    private fun setupPeriodDropdown() {
        val items = listOf(
            resources.getString(R.string.period_today),
            resources.getString(R.string.period_yesterday),
            resources.getString(R.string.period_week),
            resources.getString(R.string.period_month),
            resources.getString(R.string.period_year),
            resources.getString(R.string.period_customer)
        )

        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, items)
        binding.periodDropdown.setAdapter(adapter)
        binding.periodDropdown.threshold = 0

        // Force dropdown to show when clicked
        binding.periodDropdown.setOnClickListener {
            binding.periodDropdown.showDropDown()
        }

        binding.periodDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedPeriod = PeriodFilter.fromPosition(position)

            // Show or hide custom control placeholder
            binding.customerDropdown.visibility =
                if (selectedPeriod == PeriodFilter.CUSTOMER) View.VISIBLE else View.GONE

            if (selectedPeriod == PeriodFilter.CUSTOMER) {
                showDateRangePicker()
            } else {
                customStart = null
                customEnd = null
                binding.customerDropdown.setText("")
                updateRangeCaption()
                setLoading(true)
                loadAndRender()
            }
        }

        binding.periodDropdown.setText(items.first(), false)
    }

    private fun setupChartModeToggle() {
        // default to Counts
        binding.chartModeGroup.check(R.id.btn_mode_counts)
        binding.chartModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            chartMode = if (checkedId == R.id.btn_mode_counts) ChartMode.COUNTS else ChartMode.DURATION
            // re-render chart with last stats
            lastStats?.let { renderChart(it) }
        }
    }

    private fun loadAndRender() {
        // show loader and dim content while (re)loading
        setLoading(true)
        ensureBackgroundThread {
            recentsHelper.getRecentCalls(previousRecents = emptyList(), queryLimit = Int.MAX_VALUE) { calls ->
                val filteredBySim = filterBySim(calls)
                val filteredByPeriod = filterByPeriod(filteredBySim)
                val stats = computeStats(filteredByPeriod)
                (activity as SimpleActivity).runOnUiThread {
                    updateRangeCaption()
                    // tiles
                    animateTile(binding.valueTotalCalls, lastStats?.totalCount ?: stats.totalCount, stats.totalCount)
                    setText(binding.valueTotalDuration, formatDuration(stats.totalDuration))

                    animateTile(binding.valueIncoming, lastStats?.incomingCount ?: stats.incomingCount, stats.incomingCount)
                    setText(binding.valueIncomingDuration, formatDuration(stats.incomingDuration))

                    animateTile(binding.valueOutgoing, lastStats?.outgoingCount ?: stats.outgoingCount, stats.outgoingCount)
                    setText(binding.valueOutgoingDuration, formatDuration(stats.outgoingDuration))

                    animateTile(binding.valueMissed, lastStats?.missedCount ?: stats.missedCount, stats.missedCount)
                    animateTile(binding.valueRejected, lastStats?.rejectedCount ?: stats.rejectedCount, stats.rejectedCount)
                    animateTile(binding.valueNeverAnswered, lastStats?.neverAnsweredCount ?: stats.neverAnsweredCount, stats.neverAnsweredCount)
                    animateTile(binding.valueUnique, lastStats?.uniqueNumbers ?: stats.uniqueNumbers, stats.uniqueNumbers)

                    // progress bars based on counts
                    val total = stats.totalCount.coerceAtLeast(1)
                    animateProgress(binding.progressIncoming, (binding.progressIncoming.progress), (stats.incomingCount * 100f / total).toInt())
                    animateProgress(binding.progressOutgoing, (binding.progressOutgoing.progress), (stats.outgoingCount * 100f / total).toInt())
                    animateProgress(binding.progressRejected, (binding.progressRejected.progress), ((stats.rejectedCount + stats.missedCount) * 100f / total).toInt())

                    // analysis cards
                    bindAnalysisCards(filteredByPeriod)

                    renderChart(stats)
                    lastStats = stats
                    setLoading(false)
                }
            }
        }
    }

    private fun bindAnalysisCards(calls: List<RecentCall>) {
        if (calls.isEmpty()) {
            binding.valueTopCaller.text = "-"
            binding.valueLongestCall.text = "-"
            binding.valueHighestTotalDuration.text = "-"
            binding.valueAvgDurationPerCall.text = "-"
            binding.valueAvgDurationPerDay.text = "-"
            return
        }

        // Map numbers to total durations (only answered calls: duration > 0)
        val durationByNumber = HashMap<String, Int>()
        var longest = 0
        calls.forEach { c ->
            if (c.duration > 0) {
                durationByNumber[c.phoneNumber] = (durationByNumber[c.phoneNumber] ?: 0) + c.duration
                if (c.duration > longest) longest = c.duration
            }
        }

        // Top caller by total talk time
        val topEntry = durationByNumber.maxByOrNull { it.value }
        binding.valueTopCaller.text = topEntry?.key ?: "-"

        // Longest single call
        binding.valueLongestCall.text = if (longest > 0) formatShortDuration(longest) else "-"

        // Highest total call duration (contact with max summed duration)
        binding.valueHighestTotalDuration.text = topEntry?.let { formatShortDuration(it.value) } ?: "-"

        // Averages
        val answered = calls.filter { it.duration > 0 }
        val totalAnsweredDuration = answered.sumOf { it.duration }
        val avgPerCall = if (answered.isNotEmpty()) totalAnsweredDuration / answered.size else 0
        binding.valueAvgDurationPerCall.text = if (avgPerCall > 0) formatShortDuration(avgPerCall) else "-"

        // Average per day within selected period
        val distinctDays = answered.map { dayStart(it.startTS) }.toSet().size
        val avgPerDay = if (distinctDays > 0) totalAnsweredDuration / distinctDays else 0
        binding.valueAvgDurationPerDay.text = if (avgPerDay > 0) formatShortDuration(avgPerDay) else "-"
    }

    private fun dayStart(ts: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ts
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun setTile(textView: TextView, value: Int) {
        textView.text = value.toString()
    }

    private fun setText(textView: TextView, value: String) {
        textView.text = value
    }

    private fun setProgress(bar: ProgressBar, value: Int, total: Int) {
        val percent = (value * 100f / total).toInt()
        bar.progress = percent
    }

    private fun animateTile(textView: TextView, fromValue: Int, toValue: Int) {
        if (fromValue == toValue) {
            textView.text = toValue.toString()
            return
        }
        val animator = ValueAnimator.ofInt(fromValue, toValue)
        animator.duration = 350
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { anim ->
            textView.text = (anim.animatedValue as Int).toString()
        }
        animator.start()
    }

    private fun animateProgress(bar: ProgressBar, fromPercent: Int, toPercent: Int) {
        if (fromPercent == toPercent) {
            bar.progress = toPercent
            return
        }
        val animator = ValueAnimator.ofInt(fromPercent, toPercent)
        animator.duration = 350
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { anim ->
            bar.progress = anim.animatedValue as Int
        }
        animator.start()
    }

    private fun filterBySim(calls: List<RecentCall>): List<RecentCall> {
        return when (selectedSim) {
            SimFilter.SIM1 -> calls.filter { it.simID == 1 || it.simID == -1 }
            SimFilter.SIM2 -> calls.filter { it.simID == 2 }
            SimFilter.BOTH -> calls
        }
    }

    private fun filterByPeriod(calls: List<RecentCall>): List<RecentCall> {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)

        val startOfToday = cal.timeInMillis
        val dayMillis = 24L * 60 * 60 * 1000

        val (start, end) = when (selectedPeriod) {
            PeriodFilter.TODAY -> startOfToday to (startOfToday + dayMillis)
            PeriodFilter.YESTERDAY -> (startOfToday - dayMillis) to startOfToday
            PeriodFilter.WEEK -> (startOfToday - 7 * dayMillis) to null
            PeriodFilter.MONTH -> (startOfToday - 30 * dayMillis) to null
            PeriodFilter.YEAR -> (startOfToday - 365 * dayMillis) to null
            PeriodFilter.CUSTOMER -> (customStart) to (customEnd)
        }

        return if (start == null) calls else calls.filter { call ->
            val afterStart = call.startTS >= start
            val beforeEnd = end?.let { call.startTS < it } ?: true
            afterStart && beforeEnd
        }
    }

    private fun computeStats(calls: List<RecentCall>): Stats {
        var incoming = 0
        var outgoing = 0
        var missed = 0
        var rejected = 0
        var neverAnswered = 0 // outgoing with duration 0
        var incomingDur = 0
        var outgoingDur = 0
        var totalDur = 0

        val unique = HashSet<String>()

        calls.forEach { call ->
            unique.add(call.phoneNumber)
            totalDur += call.duration
            when (call.type) {
                android.provider.CallLog.Calls.INCOMING_TYPE -> {
                    incoming++
                    incomingDur += call.duration
                }
                android.provider.CallLog.Calls.OUTGOING_TYPE -> {
                    outgoing++
                    outgoingDur += call.duration
                    if (call.duration == 0) neverAnswered++
                }
                android.provider.CallLog.Calls.MISSED_TYPE -> missed++
                android.provider.CallLog.Calls.REJECTED_TYPE -> rejected++
            }
        }

        val total = incoming + outgoing + missed + rejected
        return Stats(
            totalCount = total,
            totalDuration = totalDur,
            incomingCount = incoming,
            incomingDuration = incomingDur,
            outgoingCount = outgoing,
            outgoingDuration = outgoingDur,
            missedCount = missed,
            rejectedCount = rejected,
            neverAnsweredCount = neverAnswered,
            uniqueNumbers = unique.size
        )
    }

    private fun setupChartAppearance() {
        val chart: BarChart = binding.analyticsChart
        chart.description.isEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f
        chart.axisLeft.granularity = 1f
        chart.axisLeft.textColor = activity!!.getProperTextColor()
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            labelCount = 1
            valueFormatter = IndexAxisValueFormatter(listOf("Phone Call"))
            textColor = activity!!.getProperTextColor()
        }
        chart.legend.apply {
            isEnabled = true
            verticalAlignment = Legend.LegendVerticalAlignment.TOP
            horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)
            textColor = activity!!.getProperTextColor()
        }
    }

    private fun renderChart(stats: Stats) {
        val chart: BarChart = binding.analyticsChart

        val valuesIncoming = if (chartMode == ChartMode.COUNTS) stats.incomingCount.toFloat() else stats.incomingDuration.toFloat()
        val valuesOutgoing = if (chartMode == ChartMode.COUNTS) stats.outgoingCount.toFloat() else stats.outgoingDuration.toFloat()
        val valuesMissed = if (chartMode == ChartMode.COUNTS) stats.missedCount.toFloat() else 0f
        val valuesRejected = if (chartMode == ChartMode.COUNTS) stats.rejectedCount.toFloat() else 0f

        val entriesIncoming = listOf(BarEntry(0f, valuesIncoming))
        val entriesOutgoing = listOf(BarEntry(0.2f, valuesOutgoing))
        val entriesMissed = listOf(BarEntry(0.4f, valuesMissed))
        val entriesRejected = listOf(BarEntry(0.6f, valuesRejected))

        val colorIncoming = 0xFF4CAF50.toInt() // green
        val colorOutgoing = 0xFFFFC107.toInt() // amber
        val colorMissed = 0xFFF44336.toInt() // red
        val colorRejected = 0xFFE91E63.toInt() // pink-ish

        val dsIn = BarDataSet(entriesIncoming, resources.getString(R.string.incoming)).apply { color = colorIncoming }
        val dsOut = BarDataSet(entriesOutgoing, resources.getString(R.string.outgoing)).apply { color = colorOutgoing }
        val dsMiss = BarDataSet(entriesMissed, resources.getString(R.string.missed)).apply { color = colorMissed }
        val dsRej = BarDataSet(entriesRejected, resources.getString(R.string.rejected)).apply { color = colorRejected }

        val barData = BarData(dsIn, dsOut, dsMiss, dsRej).apply {
            setValueTextColor(activity!!.getProperTextColor())
            barWidth = 0.16f
        }

        // Axis and value formatting depending on mode
        if (chartMode == ChartMode.DURATION) {
            val vf = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return formatShortDuration(value.toInt())
                }
                override fun getBarLabel(barEntry: BarEntry?): String {
                    return formatShortDuration(barEntry?.y?.toInt() ?: 0)
                }
            }
            chart.axisLeft.valueFormatter = vf
            barData.setValueFormatter(vf)
        } else {
            val vf = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String = value.toInt().toString()
                override fun getBarLabel(barEntry: BarEntry?): String = (barEntry?.y?.toInt() ?: 0).toString()
            }
            chart.axisLeft.valueFormatter = vf
            barData.setValueFormatter(vf)
        }

        chart.data = barData
        chart.invalidate()
        chart.animateY(600)
    }

    private enum class SimFilter { SIM1, SIM2, BOTH }
    private enum class PeriodFilter { TODAY, YESTERDAY, WEEK, MONTH, YEAR, CUSTOMER; companion object {
        fun fromPosition(position: Int) = values()[position]
    } }
    private enum class ChartMode { COUNTS, DURATION }

    private data class Stats(
        val totalCount: Int,
        val totalDuration: Int,
        val incomingCount: Int,
        val incomingDuration: Int,
        val outgoingCount: Int,
        val outgoingDuration: Int,
        val missedCount: Int,
        val rejectedCount: Int,
        val neverAnsweredCount: Int,
        val uniqueNumbers: Int,
    )

    private fun showDateRangePicker() {
        val constraints = CalendarConstraints.Builder().build()
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setCalendarConstraints(constraints)
            .setTitleText(resources.getString(R.string.select_date_range))
            .build()

        picker.addOnPositiveButtonClickListener(
            MaterialPickerOnPositiveButtonClickListener { range ->
                val start = range.first
                val end = range.second
                if (start != null && end != null) {
                    // MaterialDatePicker returns end as inclusive; we use exclusive upper bound in filters
                    customStart = start
                    customEnd = end + 24L * 60 * 60 * 1000 // move to next day start
                    // Show formatted range in the field
                    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val startStr = fmt.format(Date(start))
                    val endStr = fmt.format(Date(end))
                    binding.customerDropdown.setText("$startStr - $endStr")
                    updateRangeCaption()
                    setLoading(true)
                    loadAndRender()
                }
            }
        )

        picker.show((activity as SimpleActivity).supportFragmentManager, "analytics_date_range")

        // Allow reopening the picker by clicking on the field
        binding.customerDropdown.isFocusable = false
        binding.customerDropdown.isClickable = true
        binding.customerDropdown.setOnClickListener { picker.show((activity as SimpleActivity).supportFragmentManager, "analytics_date_range") }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> resources.getString(R.string.duration_format, h, m, s)
            m > 0 -> resources.getString(R.string.duration_min_sec_format, m, s)
            else -> resources.getString(R.string.duration_sec_format, s)
        }
    }

    private fun formatShortDuration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    private fun updateRangeCaption() {
        val (start, end) = currentPeriodBounds()
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val startStr = start?.let { fmt.format(Date(it)) } ?: return
        val endInclusive = (end ?: System.currentTimeMillis()) - 1
        val endStr = fmt.format(Date(endInclusive))
        val caption = if (startStr == endStr) startStr else "$startStr - $endStr"
        binding.rangeCaption.text = caption
    }

    private fun currentPeriodBounds(): Pair<Long?, Long?> {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis
        val dayMillis = 24L * 60 * 60 * 1000
        return when (selectedPeriod) {
            PeriodFilter.TODAY -> startOfToday to (startOfToday + dayMillis)
            PeriodFilter.YESTERDAY -> (startOfToday - dayMillis) to startOfToday
            PeriodFilter.WEEK -> (startOfToday - 7 * dayMillis) to null
            PeriodFilter.MONTH -> (startOfToday - 30 * dayMillis) to null
            PeriodFilter.YEAR -> (startOfToday - 365 * dayMillis) to null
            PeriodFilter.CUSTOMER -> customStart to customEnd
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.analyticsLoader.visibility = if (loading) View.VISIBLE else View.GONE
        binding.contentContainer.alpha = if (loading) 0.3f else 1f
        binding.contentContainer.isEnabled = !loading
    }
}
