package com.apex.axpense.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PeriodTest {

    private fun cal(millis: Long) = Calendar.getInstance().apply { timeInMillis = millis }

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        Calendar.getInstance().apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    @Test
    fun month_runs_from_the_first_to_the_first_of_next_month() {
        val (start, end) = Period(2026, Calendar.MARCH).range()
        assertEquals(at(2026, Calendar.MARCH, 1, 0), start)
        assertEquals(at(2026, Calendar.APRIL, 1, 0), end)
    }

    @Test
    fun december_ends_on_january_first_of_next_year() {
        val (start, end) = Period(2026, Calendar.DECEMBER).range()
        assertEquals(at(2026, Calendar.DECEMBER, 1, 0), start)
        assertEquals(at(2027, Calendar.JANUARY, 1, 0), end)
    }

    @Test
    fun whole_year_covers_january_to_december() {
        val (start, end) = Period(2026, null).range()
        assertEquals(at(2026, Calendar.JANUARY, 1, 0), start)
        assertEquals(at(2027, Calendar.JANUARY, 1, 0), end)
    }

    @Test
    fun all_time_has_no_limits() {
        val (start, end) = Period(null, null).range()
        assertEquals(0L, start)
        assertEquals(Long.MAX_VALUE, end)
    }

    @Test
    fun last_moment_of_month_is_inside_and_first_of_next_is_outside() {
        val (start, end) = Period(2026, Calendar.FEBRUARY).range()
        val lastInstant = at(2026, Calendar.MARCH, 1, 0) - 1
        assertTrue(lastInstant in start until end)
        assertTrue(at(2026, Calendar.MARCH, 1, 0) !in start until end)
    }

    @Test
    fun current_period_is_the_month_of_the_given_time() {
        val period = Period.current(at(2026, Calendar.OCTOBER, 28))
        assertEquals(Period(2026, Calendar.OCTOBER), period)
    }

    @Test
    fun years_run_from_this_year_back_to_the_oldest_expense() {
        val now = at(2026, Calendar.OCTOBER, 28)
        assertEquals(listOf(2026, 2025, 2024), yearsToOffer(at(2024, Calendar.MAY, 3), now))
    }

    @Test
    fun years_with_no_expenses_offer_only_this_year() {
        assertEquals(listOf(2026), yearsToOffer(null, at(2026, Calendar.OCTOBER, 28)))
    }

    @Test
    fun an_expense_dated_in_the_future_does_not_add_years() {
        assertEquals(listOf(2026), yearsToOffer(at(2030, Calendar.JANUARY, 1), at(2026, Calendar.OCTOBER, 28)))
    }

    @Test
    fun year_of_helper_uses_local_time() {
        assertEquals(2026, cal(at(2026, Calendar.JUNE, 15)).get(Calendar.YEAR))
    }
}
