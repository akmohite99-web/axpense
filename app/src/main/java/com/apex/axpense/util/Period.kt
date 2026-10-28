package com.apex.axpense.util

import java.util.Calendar

/**
 * The slice of time the Home screen shows.
 * [year] null = all time; [month] (0 = January) null = the whole year.
 */
data class Period(val year: Int?, val month: Int?) {

    /** Start (inclusive) and end (exclusive) in epoch millis, in the device's time zone. */
    fun range(): Pair<Long, Long> {
        if (year == null) return 0L to Long.MAX_VALUE
        val start = Calendar.getInstance().apply {
            clear()
            set(year, month ?: Calendar.JANUARY, 1)
        }
        val end = (start.clone() as Calendar).apply {
            if (month == null) add(Calendar.YEAR, 1) else add(Calendar.MONTH, 1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    companion object {
        /** This calendar month. */
        fun current(now: Long = System.currentTimeMillis()): Period {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            return Period(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        }
    }
}

/** Years to offer in the filter: this year back to the year of the oldest expense, newest first. */
fun yearsToOffer(earliestTimestamp: Long?, now: Long = System.currentTimeMillis()): List<Int> {
    val thisYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
    val firstYear = earliestTimestamp?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.YEAR)
    } ?: thisYear
    return (thisYear downTo minOf(firstYear, thisYear)).toList()
}
