package com.billbox.app.reminders

import com.billbox.app.data.Bill
import com.billbox.app.data.Category
import com.billbox.app.data.Product
import com.billbox.app.data.RecurringBill
import com.billbox.app.util.Dates
import com.billbox.app.util.Money
import java.time.LocalDate
import java.time.YearMonth

data class Reminder(
    /** Stable id so the same reminder is never sent twice. */
    val key: String,
    val title: String,
    val body: String,
    val kind: Kind,
) {
    enum class Kind { WARRANTY, DUE, RETURN, BUDGET }
}

/** Decides which reminders are due on [today]. Pure, so it is unit-tested. */
object ReminderLogic {
    val warrantyDays = setOf(30L, 7L, 1L)
    val returnDays = setOf(3L, 1L, 0L)

    fun due(
        today: LocalDate,
        products: List<Product>,
        recurring: List<RecurringBill>,
        bills: List<Bill>,
        categories: List<Category>,
    ): List<Reminder> = buildList {
        for (p in products) {
            val expiry = p.warrantyExpiry
            val days = Dates.daysUntil(expiry, today)
            if (days in warrantyDays) {
                add(Reminder(
                    key = "w-${p.id}-$expiry-$days",
                    title = "Warranty ending ${Dates.relative(expiry, today)}",
                    body = "${p.name} warranty ends on ${Dates.format(expiry)}. Check it now if anything needs repair.",
                    kind = Reminder.Kind.WARRANTY,
                ))
            }
        }
        for (r in recurring.filter { it.isActive }) {
            val days = Dates.daysUntil(r.nextDueDate, today)
            val amount = r.amountPaise?.let { " · ${Money.format(it)}" } ?: ""
            val fire = if (r.autopay) days == 0L else days == r.remindDaysBefore.toLong() || days == 0L || days == -1L
            if (fire) {
                add(Reminder(
                    key = "r-${r.id}-${r.nextDueDate}-$days",
                    title = when {
                        days < 0 -> "${r.name} is overdue"
                        r.autopay -> "${r.name} auto-debit today"
                        else -> "${r.name} due ${Dates.relative(r.nextDueDate, today)}"
                    },
                    body = "Due ${Dates.format(r.nextDueDate)}$amount",
                    kind = Reminder.Kind.DUE,
                ))
            }
        }
        for (b in bills) {
            val by = b.returnByDate ?: continue
            val days = Dates.daysUntil(by, today)
            if (days in returnDays) {
                add(Reminder(
                    key = "ret-${b.id}-$by-$days",
                    title = "Return window closes ${Dates.relative(by, today)}",
                    body = "${b.merchant} · ${Money.format(b.totalPaise)}",
                    kind = Reminder.Kind.RETURN,
                ))
            }
        }
        val month = YearMonth.from(today)
        val spent = monthSpendByCategory(bills, month)
        for (c in categories) {
            val budget = c.monthlyBudgetPaise ?: continue
            if (budget <= 0) continue
            val used = spent[c.id] ?: 0
            val level = when {
                used >= budget -> 100
                used * 100 >= budget * 80 -> 80
                else -> continue
            }
            add(Reminder(
                key = "b-${c.id}-$month-$level",
                title = if (level == 100) "${c.name} budget exceeded" else "${c.name} budget 80% used",
                body = "${Money.formatShort(used)} of ${Money.formatShort(budget)} spent in ${Dates.format(month)}",
                kind = Reminder.Kind.BUDGET,
            ))
        }
    }

    fun monthSpendByCategory(bills: List<Bill>, month: YearMonth): Map<Long?, Long> =
        bills.filter { YearMonth.from(it.billDate) == month }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.totalPaise } }
}
