package org.fossify.phone.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.phone.R
import org.fossify.phone.activities.TotalCallsDetailActivity.FilterType
import org.fossify.phone.adapters.RecentsAdapter
import org.fossify.phone.databinding.ActivityFilteredCallsBinding
import org.fossify.phone.helpers.RecentsHelper
import org.fossify.phone.models.RecentCall
import java.util.Calendar
import java.util.logging.Filter


class FilteredCallsActivity : SimpleActivity() {
    private lateinit var binding: ActivityFilteredCallsBinding
    private val recentsHelper = RecentsHelper(this)
    private var filterType: String = ""
    private var filterTitle: String = ""
    private var currentFilterType: FilteredCallsActivity.FilterType = FilteredCallsActivity.FilterType.TODAY
    private var customStartDate: Long = 0
    private var customEndDate: Long = 0
    private enum class FilterType {
        TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, CUSTOM
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivityFilteredCallsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar(binding.toolbar)
        setupNavigationIcon()
        getIntentExtras()
        setupAdapter()
        setupPeriodDropdown()
        loadFilteredCalls()
    }
    private fun setFilter(filterType: FilteredCallsActivity.FilterType) {
        currentFilterType = filterType

        // Update the date range based on the selected filter
        val calendar = Calendar.getInstance()

        when (filterType) {
            FilteredCallsActivity.FilterType.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                customStartDate = calendar.timeInMillis
                customEndDate = System.currentTimeMillis()
            }
            FilteredCallsActivity.FilterType.YESTERDAY -> {
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
            FilteredCallsActivity.FilterType.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                customStartDate = calendar.timeInMillis
                customEndDate = System.currentTimeMillis()
            }
            FilteredCallsActivity.FilterType.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                customStartDate = calendar.timeInMillis
                customEndDate = System.currentTimeMillis()
            }
            FilteredCallsActivity.FilterType.CUSTOM -> {
                // Custom dates are set in the date picker dialogs
            }
        }
        loadFilteredCalls()

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
                currentFilterType = FilteredCallsActivity.FilterType.CUSTOM
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerEnd.setTitle("Select End Date")
        datePickerEnd.show()
        loadFilteredCalls()
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


    private fun getIntentExtras() {
        filterType = intent.getStringExtra("filter_type") ?: ""
        filterTitle = when (filterType) {
            "incoming" -> getString(R.string.incoming_calls)
            "outgoing" -> getString(R.string.outgoing_calls)
            "missed" -> getString(R.string.missed_calls)
            "rejected" -> getString(R.string.rejected_calls)
            else -> getString(R.string.total_calls)
        }

        supportActionBar?.title = filterTitle
    }

    private fun setupNavigationIcon() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupAdapter() {
        binding.callsRecyclerView.apply {
            adapter = RecentsAdapter(
                items = emptyList(),
                onItemClick = { /* Handle item click if needed */ }
            )
            setHasFixedSize(true)
        }
    }

    private fun loadFilteredCalls() {
        binding.loadingIndicator.beVisible()
        binding.emptyState.beInvisible()

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
                val filteredCalls = filterCallsByType(calls)
                val filteredCallsByDate = filterCallsByDate(filteredCalls)
                updateCallsList(filteredCallsByDate)
                binding.loadingIndicator.beInvisible()
                binding.emptyState.beVisibleIf(filteredCalls.isEmpty())
            }
        }
    }

    private fun filterCallsByType(calls: List<RecentCall>): List<RecentCall> {
        return when (filterType) {
            "incoming" -> calls.filter { it.type == 1 } // INCOMING
            "outgoing" -> calls.filter { it.type == 2 } // OUTGOING
            "missed" -> calls.filter { it.type == 3 } // MISSED
            "rejected" -> calls.filter { it.type == 5 } // REJECTED
            else -> calls // All calls
        }
    }

    private fun filterCallsByDate(calls: List<RecentCall>): List<RecentCall> {
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()

        return when (currentFilterType) {
            FilteredCallsActivity.FilterType.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis
                calls.filter { it.startTS in startOfDay..now }
            }

            FilteredCallsActivity.FilterType.YESTERDAY -> {
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

            FilteredCallsActivity.FilterType.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfWeek = calendar.timeInMillis
                calls.filter { it.startTS in startOfWeek..now }
            }

            FilteredCallsActivity.FilterType.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis
                calls.filter { it.startTS in startOfMonth..now }
            }
            FilteredCallsActivity.FilterType.CUSTOM -> {
                calls.filter { it.startTS in customStartDate..customEndDate }
            }
        }
    }


    private fun updateCallsList(calls: List<RecentCall>) {
        (binding.callsRecyclerView.adapter as? RecentsAdapter)?.updateItems(calls)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
