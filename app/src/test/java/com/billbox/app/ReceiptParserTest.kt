package com.billbox.app

import com.billbox.app.ocr.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 10, 24)

    private val supermarket = """
        TAX INVOICE
        FRESHWAY SUPERMART
        12, MG Road, Bengaluru
        GSTIN: 29ABCDE1234F1Z5
        Bill No: FW/2026/04512    Date: 18/10/2026
        Milk 1L          2   64.00
        Rice 5kg         1  425.00
        Sub Total             489.00
        CGST @2.5%             12.23
        SGST @2.5%             12.23
        Grand Total           513.46
        Thank you, visit again
    """.trimIndent()

    @Test fun readsAllFieldsFromSupermarketBill() {
        val p = ReceiptParser.parse(supermarket, today)
        assertEquals("FRESHWAY SUPERMART", p.merchant)
        assertEquals(LocalDate.of(2026, 10, 18), p.date)
        assertEquals(51346L, p.totalPaise)
        assertEquals(2446L, p.taxPaise)
        assertEquals("29ABCDE1234F1Z5", p.gstin)
        assertEquals("FW/2026/04512", p.invoiceNo)
        assertEquals("Groceries", p.suggestedCategory)
    }

    @Test fun grandTotalBeatsPlainTotalAndIgnoresSubtotal() {
        val text = "Cafe Mocha\nSubtotal 900.00\nTotal 945.00\nGrand Total 1,045.50\nTotal items 4"
        assertEquals(104550L, ReceiptParser.parse(text, today).totalPaise)
    }

    @Test fun readsIndianGroupedAmounts() {
        assertEquals(12345650L, ReceiptParser.parse("Croma\nNet Amount Rs. 1,23,456.50", today).totalPaise)
    }

    @Test fun totalOnNextLine() {
        assertEquals(29900L, ReceiptParser.parse("Shop\nAmount Payable\n299.00", today).totalPaise)
    }

    @Test fun parsesMonthNameAndIsoDates() {
        assertEquals(LocalDate.of(2026, 3, 5), ReceiptParser.findDate("Date: 05-Mar-2026", today))
        assertEquals(LocalDate.of(2025, 12, 31), ReceiptParser.findDate("2025-12-31 18:22", today))
        assertEquals(LocalDate.of(2026, 9, 2), ReceiptParser.findDate("02/09/26", today))
    }

    @Test fun ignoresFutureDates() {
        assertNull(ReceiptParser.findDate("Valid till 31/12/2030", today))
    }

    @Test fun fallsBackToLargestDecimalAmount() {
        assertEquals(78050L, ReceiptParser.parse("Indian Oil\nPetrol 7.80 L\n100.06\n780.50", today).totalPaise)
        assertEquals("Fuel", ReceiptParser.parse("Indian Oil petrol pump", today).suggestedCategory)
    }
}
