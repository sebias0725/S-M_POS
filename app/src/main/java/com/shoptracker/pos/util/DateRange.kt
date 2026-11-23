package com.shoptracker.pos.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Inclusive date range in the phone's local time zone. */
data class DateRange(val startDate: LocalDate, val endDate: LocalDate) {
    val startMillis: Long
        get() = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Exclusive upper bound (start of the day after endDate). */
    val endMillis: Long
        get() = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val dayCount: Long get() = ChronoUnit.DAYS.between(startDate, endDate) + 1

    fun label(): String {
        val f = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        return if (startDate == endDate) startDate.format(f) else startDate.format(f) + " - " + endDate.format(f)
    }

    companion object {
        fun today(): DateRange = LocalDate.now().let { DateRange(it, it) }
        fun lastWeek(): DateRange = LocalDate.now().let { DateRange(it.minusDays(6), it) }
        fun lastMonth(): DateRange = LocalDate.now().let { DateRange(it.minusDays(29), it) }

        /** Date picker values are UTC midnight milliseconds. */
        fun fromPickerMillis(startUtc: Long, endUtc: Long?): DateRange {
            val s = Instant.ofEpochMilli(startUtc).atZone(ZoneOffset.UTC).toLocalDate()
            val e = if (endUtc == null) s else Instant.ofEpochMilli(endUtc).atZone(ZoneOffset.UTC).toLocalDate()
            return if (e.isBefore(s)) DateRange(e, s) else DateRange(s, e)
        }
    }
}

fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
