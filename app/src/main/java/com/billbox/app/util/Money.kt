package com.billbox.app.util

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    /** ₹1,23,456.50 — Indian digit grouping, done by hand so it is the same on every device. */
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        return "$sign₹${group(abs / 100)}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /** ₹1,23,457 — rounded to whole rupees, for charts and summaries. */
    fun formatShort(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        return "$sign₹${group((kotlin.math.abs(paise) + 50) / 100)}"
    }

    /** "1 bill", "3 bills". */
    fun bills(count: Int): String = if (count == 1) "1 bill" else "$count bills"

    /** 1234567 → "12,34,567": last three digits, then pairs. */
    private fun group(rupees: Long): String {
        val s = rupees.toString()
        if (s.length <= 3) return s
        val head = s.dropLast(3)
        return head.reversed().chunked(2).joinToString(",").reversed() + "," + s.takeLast(3)
    }

    /** "1,234.5" or "₹ 1234.50" → 123450. Returns null when the text is not a number. */
    fun parse(text: String): Long? {
        val cleaned = text.replace("₹", "").replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true).replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.movePointRight(2)?.toLong()
    }

    /** 123450 → "1234.50" for edit fields; whole rupees drop the decimals. */
    fun toInput(paise: Long): String =
        if (paise % 100 == 0L) (paise / 100).toString() else BigDecimal.valueOf(paise, 2).toPlainString()
}
