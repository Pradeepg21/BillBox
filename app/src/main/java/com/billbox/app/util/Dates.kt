package com.billbox.app.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {
    private val display = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    fun format(date: LocalDate): String = date.format(display)
    fun format(month: YearMonth): String = month.format(this.month)
    fun shortMonth(month: YearMonth): String = month.format(shortMonth)

    fun daysUntil(date: LocalDate, today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, date)

    /** "in 5 days", "tomorrow", "today", "3 days ago". */
    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val d = daysUntil(date, today)
        return when {
            d == 0L -> "today"
            d == 1L -> "tomorrow"
            d == -1L -> "yesterday"
            d > 1 && d < 60 -> "in $d days"
            d >= 60 -> "in ${d / 30} months"
            d > -60 -> "${-d} days ago"
            else -> "${-d / 30} months ago"
        }
    }

    /** Indian financial year containing [date], e.g. 1 Apr 2026 – 31 Mar 2027. */
    fun financialYear(date: LocalDate): ClosedRange<LocalDate> {
        val startYear = if (date.monthValue >= 4) date.year else date.year - 1
        return LocalDate.of(startYear, 4, 1)..LocalDate.of(startYear + 1, 3, 31)
    }

    /** Material date pickers work in UTC millis. */
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    fun fromPickerMillis(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, 86_400_000L))
}
