package com.example.spendsync.ui.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * Shared, hoistable state for the currently-selected month/year filter.
 *
 * All bottom-nav tabs receive this same instance from [MainScreen], so switching
 * tabs preserves the selected date and every screen acts on the same filter.
 */
class DateFilterState(initialDate: LocalDate = LocalDate.now()) {

    var selectedDate: LocalDate by mutableStateOf(initialDate)
        private set

    /** Alias for UI components that refer to this as the selected month. */
    val selectedMonth: LocalDate get() = selectedDate

    /** Global flag to show/hide the month picker dialog. */
    var showMonthPicker: Boolean by mutableStateOf(false)

    /** Move one calendar month forward. */
    fun nextMonth() {
        selectedDate = selectedDate.plusMonths(1)
    }

    /** Move one calendar month backward. */
    fun prevMonth() {
        selectedDate = selectedDate.minusMonths(1)
    }

    /** Jump directly to any date (month + year are what matter). */
    fun selectDate(date: LocalDate) {
        selectedDate = date
    }
}
