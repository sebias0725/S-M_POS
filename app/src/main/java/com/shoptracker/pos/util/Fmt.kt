package com.shoptracker.pos.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatting and money parsing helpers. Money is always whole centavos (Long). */
object Fmt {
    private const val MAX_CENTAVOS = 100_000_000_00L // 100 million pesos safety cap

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(ZoneId.systemDefault())

    private fun group(centavos: Long): String {
        val df = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
        return df.format(BigDecimal.valueOf(Math.abs(centavos), 2))
    }

    fun peso(centavos: Long): String =
        (if (centavos < 0) "-" else "") + "₱" + group(centavos)

    fun signedPeso(centavos: Long): String = when {
        centavos > 0 -> "+₱" + group(centavos)
        centavos < 0 -> "-₱" + group(centavos)
        else -> "₱0.00"
    }

    /** Plain decimal text such as "1234.50" (for spreadsheets / editable fields). */
    fun plain(centavos: Long): String = BigDecimal.valueOf(centavos, 2).toPlainString()

    /** Parses user input such as "1,250.5" into centavos. Returns null if invalid or negative. */
    fun parsePeso(input: String): Long? {
        val t = input.trim().replace(",", "").replace("₱", "")
        if (t.isEmpty()) return null
        return try {
            val bd = BigDecimal(t)
            if (bd.signum() < 0) return null
            val centavos = bd.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
            if (centavos > MAX_CENTAVOS) null else centavos
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun date(millis: Long): String = formatter("EEE, MMM d, yyyy").format(Instant.ofEpochMilli(millis))
    fun shortDate(millis: Long): String = formatter("MMM d, yyyy").format(Instant.ofEpochMilli(millis))
    fun dayMonth(millis: Long): String = formatter("MMM d").format(Instant.ofEpochMilli(millis))
    fun time(millis: Long): String = formatter("h:mm a").format(Instant.ofEpochMilli(millis))
    fun isoDate(millis: Long): String = formatter("yyyy-MM-dd").format(Instant.ofEpochMilli(millis))
    fun isoTime(millis: Long): String = formatter("HH:mm:ss").format(Instant.ofEpochMilli(millis))
    fun isoDateTime(millis: Long): String = formatter("yyyy-MM-dd HH:mm:ss").format(Instant.ofEpochMilli(millis))

    /** The phone's local calendar date of a timestamp. */
    fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun localDateText(d: LocalDate): String =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()).format(d)

    /** Date-picker millis are UTC midnight of the chosen day. */
    fun pickerMillisToDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun dateToPickerMillis(d: LocalDate): Long =
        d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Percentage change from prev to curr, 2 decimals. Null when prev is zero (undefined). */
    fun growth(curr: Long, prev: Long): BigDecimal? =
        if (prev == 0L) null
        else BigDecimal(curr - prev).multiply(BigDecimal(100))
            .divide(BigDecimal(prev), 2, RoundingMode.HALF_UP)

    fun signedPct(value: BigDecimal): String =
        (if (value.signum() > 0) "+" else "") + value.toPlainString() + "%"

    /** units / target as a percentage with 1 decimal. Null when target is 0. */
    fun targetPct(units: Int, target: Int): BigDecimal? =
        if (target <= 0) null
        else BigDecimal(units).multiply(BigDecimal(100))
            .divide(BigDecimal(target), 1, RoundingMode.HALF_UP)

    /** units / target as a fraction with 4 decimals, for spreadsheet percent cells. */
    fun targetFraction(units: Int, target: Int): String? =
        if (target <= 0) null
        else BigDecimal(units).divide(BigDecimal(target), 4, RoundingMode.HALF_UP).toPlainString()
}
