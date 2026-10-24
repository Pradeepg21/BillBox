package com.billbox.app

import com.billbox.app.data.Bill
import com.billbox.app.data.Category
import com.billbox.app.data.Frequency
import com.billbox.app.data.Product
import com.billbox.app.data.RecurringBill
import com.billbox.app.data.RecurringType
import com.billbox.app.reminders.Reminder
import com.billbox.app.reminders.ReminderLogic
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyTest {
    @Test fun parsesAndFormatsRupees() {
        assertEquals(123450L, Money.parse("1,234.5"))
        assertEquals(9900L, Money.parse("₹ 99"))
        assertNull(Money.parse("abc"))
        assertEquals("1234.50", Money.toInput(123450))
        assertEquals("99", Money.toInput(9900))
        assertEquals("₹1,23,456.50", Money.format(12345650))
        assertEquals("₹12,34,567", Money.formatShort(123456650))
        assertEquals("₹999.05", Money.format(99905))
        assertTrue(Money.formatShort(-5000).startsWith("-"))
    }
}

class DatesTest {
    @Test fun financialYearRunsAprilToMarch() {
        val fy = Dates.financialYear(LocalDate.of(2027, 2, 10))
        assertEquals(LocalDate.of(2026, 4, 1), fy.start)
        assertEquals(LocalDate.of(2027, 3, 31), fy.endInclusive)
    }
}

class ReminderLogicTest {
    private val today = LocalDate.of(2026, 10, 24)

    @Test fun warrantyRemindersFireAt30And7And1Days() {
        val products = listOf(30L, 7L, 1L, 10L).mapIndexed { i, days ->
            // 12-month warranty bought (12 months − days) ago expires in exactly `days` days.
            Product(id = i + 1L, name = "P$i", purchaseDate = today.plusDays(days).minusMonths(12), warrantyMonths = 12)
        }
        val due = ReminderLogic.due(today, products, emptyList(), emptyList(), emptyList())
        assertEquals(3, due.count { it.kind == Reminder.Kind.WARRANTY })
    }

    @Test fun recurringFiresOnReminderDayAndDueDayButAutopayOnlyOnDueDay() {
        val r = RecurringBill(id = 1, name = "Rent", type = RecurringType.RENT, nextDueDate = today.plusDays(3), remindDaysBefore = 3)
        assertEquals(1, ReminderLogic.due(today, emptyList(), listOf(r), emptyList(), emptyList()).size)
        assertEquals(0, ReminderLogic.due(today, emptyList(), listOf(r.copy(autopay = true)), emptyList(), emptyList()).size)
        assertEquals(1, ReminderLogic.due(today, emptyList(), listOf(r.copy(autopay = true, nextDueDate = today)), emptyList(), emptyList()).size)
    }

    @Test fun budgetAlertAt80PercentAndOver() {
        val food = Category(id = 3, name = "Food", icon = "food", color = 0, monthlyBudgetPaise = 100_00)
        val bills = listOf(Bill(merchant = "A", billDate = today, totalPaise = 85_00, categoryId = 3))
        val r = ReminderLogic.due(today, emptyList(), emptyList(), bills, listOf(food))
        assertEquals(listOf("b-3-2026-10-80"), r.map { it.key })
        val over = ReminderLogic.due(today, emptyList(), emptyList(), bills + bills, listOf(food))
        assertEquals("b-3-2026-10-100", over.single().key)
    }

    @Test fun extendedWarrantyAddsToExpiry() {
        val p = Product(name = "TV", purchaseDate = LocalDate.of(2026, 1, 15), warrantyMonths = 12, extendedWarrantyMonths = 24)
        assertEquals(LocalDate.of(2029, 1, 15), p.warrantyExpiry)
        assertEquals(3, Frequency.QUARTERLY.months.toInt())
    }
}
