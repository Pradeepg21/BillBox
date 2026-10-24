package com.billbox.app.ocr

import java.time.DateTimeException
import java.time.LocalDate

data class ParsedReceipt(
    val merchant: String? = null,
    val date: LocalDate? = null,
    val totalPaise: Long? = null,
    val taxPaise: Long? = null,
    val gstin: String? = null,
    val invoiceNo: String? = null,
    /** Name of a default category, e.g. "Fuel". */
    val suggestedCategory: String? = null,
)

/**
 * Pulls bill fields out of raw OCR text from Indian receipts and invoices.
 * Pure Kotlin so it can be unit-tested without a device.
 */
object ReceiptParser {

    private val gstinRegex = Regex("""\b\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]\b""")
    private val invoiceRegex = Regex(
        """\b(?:invoice|inv|bill|receipt|txn|order)\s*(?:no|number|num|id|#)\s*\.?\s*[:#\-]?\s*([A-Z0-9][A-Z0-9/\-]{2,})""",
        RegexOption.IGNORE_CASE,
    )
    private val dmy = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})\b""")
    private val ymd = Regex("""\b(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})\b""")
    private val dMonY = Regex(
        """\b(\d{1,2})(?:st|nd|rd|th)?[\s\-]*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\s\-,']*(\d{4}|\d{2})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val amountRegex = Regex("""(?<![\d.,])(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+\.\d{1,2}|\d+)(?![\d%])""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /** Ordered best first: "grand total" beats a plain "total". */
    private val totalKeywords = listOf(
        "grand total", "net payable", "amount payable", "total payable", "net amount", "bill amount",
        "invoice total", "total amount", "amount due", "net total", "total",
    )
    private val notTotal = listOf("sub total", "subtotal", "sub-total", "total qty", "total items", "total item",
        "savings", "discount", "total tax", "total gst", "tax total", "round")
    private val taxPrefixes = listOf("cgst", "sgst", "utgst", "igst")

    private val merchantSkip = listOf("tax invoice", "invoice", "receipt", "gstin", "gst no", "welcome", "original",
        "duplicate", "cash memo", "bill of supply", "phone", "ph:", "tel", "mob", "date", "customer copy", "retail")

    private val categoryKeywords = linkedMapOf(
        "Fuel" to listOf("petrol", "diesel", "fuel", "indian oil", "iocl", "bharat petroleum", "bpcl", "hpcl", "hindustan petroleum", "shell"),
        "Medical" to listOf("pharma", "medical", "chemist", "hospital", "clinic", "apollo", "medplus", "diagnostic", "pathology", "1mg", "netmeds"),
        "Groceries" to listOf("mart", "supermarket", "grocery", "kirana", "fresh", "dmart", "reliance smart", "more retail", "bigbasket", "blinkit", "zepto", "jiomart"),
        "Food & Dining" to listOf("restaurant", "cafe", "café", "hotel", "dhaba", "pizza", "burger", "kitchen", "swiggy", "zomato", "food", "biryani", "bakery"),
        "Electronics" to listOf("electronics", "croma", "reliance digital", "vijay sales", "mobile", "laptop", "samsung", "apple", "lg ", "sony", "television", "refrigerator"),
        "Utilities" to listOf("electricity", "bescom", "msedcl", "tata power", "water bill", "gas", "broadband", "airtel", "jio", "bsnl", "vodafone", "recharge"),
        "Travel" to listOf("irctc", "railway", "airlines", "indigo", "air india", "uber", "ola", "rapido", "redbus", "makemytrip", "boarding"),
        "Education" to listOf("school", "college", "tuition", "university", "academy", "books", "coaching"),
        "Insurance" to listOf("insurance", "premium", "policy no", "lic "),
        "Shopping" to listOf("fashion", "apparel", "footwear", "lifestyle", "trends", "westside", "pantaloons", "amazon", "flipkart", "myntra", "decathlon"),
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lower = text.lowercase()
        return ParsedReceipt(
            merchant = findMerchant(lines),
            date = findDate(text, today),
            totalPaise = findTotal(lines),
            taxPaise = findTax(lines),
            gstin = gstinRegex.find(text.uppercase())?.value,
            invoiceNo = invoiceRegex.find(text)?.groupValues?.get(1)?.uppercase(),
            suggestedCategory = categoryKeywords.entries.firstOrNull { (_, words) -> words.any { it in lower } }?.key,
        )
    }

    internal fun findMerchant(lines: List<String>): String? =
        lines.take(6).firstOrNull { line ->
            val l = line.lowercase()
            line.count { it.isLetter() } >= 3 && merchantSkip.none { l.startsWith(it) || l == it } &&
                gstinRegex.find(line.uppercase()) == null
        }?.trim(' ', '*', '-', '=', '.', ':', '|')

    internal fun findDate(text: String, today: LocalDate): LocalDate? {
        val candidates = mutableListOf<Pair<Int, LocalDate>>()
        ymd.findAll(text).forEach { m ->
            val (y, mo, d) = m.destructured
            date(y.toInt(), mo.toInt(), d.toInt())?.let { candidates += m.range.first to it }
        }
        dmy.findAll(text).forEach { m ->
            val (d, mo, y) = m.destructured
            date(year(y), mo.toInt(), d.toInt())?.let { candidates += m.range.first to it }
        }
        dMonY.findAll(text).forEach { m ->
            val (d, mon, y) = m.destructured
            val mo = months.indexOf(mon.lowercase().take(3)) + 1
            date(year(y), mo, d.toInt())?.let { candidates += m.range.first to it }
        }
        return candidates.sortedBy { it.first }.map { it.second }
            .firstOrNull { !it.isAfter(today.plusDays(1)) && it.year >= 2000 }
    }

    internal fun findTotal(lines: List<String>): Long? {
        var best: Pair<Int, Long>? = null // keyword rank to amount; later lines win ties
        lines.forEachIndexed { i, line ->
            val l = line.lowercase()
            if (notTotal.any { it in l }) return@forEachIndexed
            val rank = totalKeywords.indexOfFirst { it in l }
            if (rank < 0) return@forEachIndexed
            val amount = lastAmount(line) ?: lines.getOrNull(i + 1)?.let(::lastAmount) ?: return@forEachIndexed
            if (amount <= 0) return@forEachIndexed
            if (best == null || rank <= best.first) best = rank to amount
        }
        best?.let { return it.second }
        // No labelled total: fall back to the largest amount written with paise.
        return lines.flatMap { line -> amountRegex.findAll(line).map { it.value } }
            .filter { '.' in it }
            .mapNotNull(::toPaise)
            .filter { it in 1..10_000_000_00 }
            .maxOrNull()
    }

    internal fun findTax(lines: List<String>): Long? {
        val parts = taxPrefixes.mapNotNull { prefix ->
            lines.firstOrNull { it.lowercase().trimStart().startsWith(prefix) }?.let(::lastAmount)
        }
        if (parts.isNotEmpty()) return parts.sum()
        return lines.firstOrNull { l -> listOf("total tax", "total gst", "gst amount", "tax amount").any { it in l.lowercase() } }
            ?.let(::lastAmount)
    }

    internal fun lastAmount(line: String): Long? =
        amountRegex.findAll(line).map { it.value }.lastOrNull()?.let(::toPaise)

    internal fun toPaise(value: String): Long? = value.replace(",", "").toBigDecimalOrNull()
        ?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()

    private fun year(y: String): Int = if (y.length == 2) 2000 + y.toInt() else y.toInt()

    private fun date(y: Int, m: Int, d: Int): LocalDate? = try {
        LocalDate.of(y, m, d)
    } catch (_: DateTimeException) {
        null
    }
}
