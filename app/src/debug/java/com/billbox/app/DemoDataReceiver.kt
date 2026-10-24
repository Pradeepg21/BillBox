package com.billbox.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.billbox.app.data.Bill
import com.billbox.app.data.BillFile
import com.billbox.app.data.BillSource
import com.billbox.app.data.Frequency
import com.billbox.app.data.PaymentMethod
import com.billbox.app.data.Product
import com.billbox.app.data.RecurringBill
import com.billbox.app.data.RecurringType
import com.billbox.app.data.TaxSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Debug builds only: replaces all data with a realistic sample set for store screenshots.
 * adb shell am broadcast -n com.billbox.app/.DemoDataReceiver
 */
class DemoDataReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = (context.applicationContext as BillBoxApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                seed(context, c)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun seed(context: Context, c: AppContainer) {
        val repo = c.repository
        repo.deleteEverything()
        val cats = repo.categories.first().associateBy { it.name }
        fun cat(name: String) = cats.getValue(name).id
        val today = LocalDate.now()
        fun d(daysAgo: Long) = today.minusDays(daysAgo)

        // Budgets
        listOf("Groceries" to 8_000_00L, "Food & Dining" to 4_000_00L, "Fuel" to 5_000_00L, "Shopping" to 6_000_00L)
            .forEach { (name, budget) -> repo.saveCategory(cats.getValue(name).copy(monthlyBudgetPaise = budget)) }

        // Bills this month
        val tvId = repo.saveBill(
            Bill(
                merchant = "Croma Electronics", billDate = d(30), totalPaise = 54_990_00, taxPaise = 8_388_31,
                gstin = "29AABCI1234F1Z5", invoiceNo = "CRM/BLR/24518", paymentMethod = PaymentMethod.CARD,
                categoryId = cat("Electronics"), notes = "Living room TV", tags = "home",
                source = BillSource.CAMERA,
            ),
            listOf(receipt(context, "CROMA", "Phoenix Marketcity, Bengaluru", d(30), "CRM/BLR/24518", listOf(
                "Samsung 55\" Crystal 4K TV" to "46,601.69", "Wall mount kit" to "0.00",
            ), "4,194.15", "4,194.16", "54,990.00")),
        )
        repo.saveBill(
            Bill(
                merchant = "DMart", billDate = d(4), totalPaise = 3_284_50, taxPaise = 156_40,
                invoiceNo = "DM0927-88412", paymentMethod = PaymentMethod.UPI, categoryId = cat("Groceries"),
                tags = "monthly", source = BillSource.CAMERA,
            ),
            listOf(receipt(context, "D MART", "Avenue Supermarts Ltd, HSR Layout", d(4), "DM0927-88412", listOf(
                "Aashirvaad Atta 10kg" to "520.00", "Fortune Sunflower Oil 5L" to "845.00", "Amul Butter 500g" to "285.00",
                "Tata Salt 1kg x2" to "56.00", "Basmati Rice 5kg" to "689.00", "Fruits & vegetables" to "733.10",
            ), "78.20", "78.20", "3,284.50")),
        )
        listOf(
            Bill(merchant = "Swiggy · Meghana Foods", billDate = d(2), totalPaise = 648_00, paymentMethod = PaymentMethod.UPI, categoryId = cat("Food & Dining")),
            Bill(merchant = "Apollo Pharmacy", billDate = d(9), totalPaise = 1_146_00, taxPaise = 54_57, paymentMethod = PaymentMethod.UPI, categoryId = cat("Medical"), taxSection = TaxSection.S80D),
            Bill(merchant = "Indian Oil, Koramangala", billDate = d(12), totalPaise = 2_000_00, paymentMethod = PaymentMethod.CARD, categoryId = cat("Fuel")),
            Bill(merchant = "BESCOM electricity", billDate = d(19), totalPaise = 1_872_00, paymentMethod = PaymentMethod.NET_BANKING, categoryId = cat("Utilities")),
            Bill(merchant = "House rent · October", billDate = d(23), totalPaise = 22_000_00, paymentMethod = PaymentMethod.NET_BANKING, categoryId = cat("Rent"), taxSection = TaxSection.HRA),
            Bill(merchant = "Amazon · Nike running shoes", billDate = d(5), totalPaise = 3_499_00, paymentMethod = PaymentMethod.CARD, categoryId = cat("Shopping"), returnByDate = today.plusDays(3)),
            Bill(merchant = "Third Wave Coffee", billDate = d(1), totalPaise = 420_00, paymentMethod = PaymentMethod.UPI, categoryId = cat("Food & Dining")),
            Bill(merchant = "Zepto", billDate = d(3), totalPaise = 1_215_00, paymentMethod = PaymentMethod.UPI, categoryId = cat("Groceries")),
            Bill(merchant = "Uber", billDate = d(7), totalPaise = 386_00, paymentMethod = PaymentMethod.UPI, categoryId = cat("Travel"), isBusiness = true),
        ).forEach { repo.saveBill(it, emptyList()) }

        // Eleven earlier months so the trend chart has history.
        val monthly = listOf(31_200L, 28_450L, 36_900L, 33_100L, 41_800L, 29_700L, 34_600L, 38_250L, 30_900L, 35_400L, 39_950L)
        monthly.forEachIndexed { i, rupees ->
            val day = today.minusMonths(i + 1L).withDayOfMonth(10)
            val parts = listOf(
                "Rent" to 22_000L, "Groceries" to rupees * 18 / 100, "Food & Dining" to rupees * 9 / 100,
                "Fuel" to rupees * 7 / 100, "Utilities" to rupees * 5 / 100,
            )
            val rest = rupees - parts.sumOf { it.second }
            (parts + ("Shopping" to rest)).forEach { (name, amount) ->
                repo.saveBill(Bill(merchant = merchantFor(name), billDate = day, totalPaise = amount * 100, categoryId = cat(name),
                    paymentMethod = PaymentMethod.UPI, taxSection = if (name == "Rent") TaxSection.HRA else null), emptyList())
            }
        }

        // Products and warranties
        repo.saveProduct(Product(billId = tvId, name = "Samsung 55\" Crystal 4K TV", brand = "Samsung", modelNo = "UA55CUE60AKLXL",
            serialNo = "0DQ43CAR600812M", purchaseDate = d(30), warrantyMonths = 12, extendedWarrantyMonths = 24, serviceCenterPhone = "18005726726"))
        repo.saveProduct(Product(name = "LG Double Door Refrigerator", brand = "LG", modelNo = "GL-S292RPZY", serialNo = "412KRSZ0B871",
            purchaseDate = today.plusDays(17).minusMonths(24), warrantyMonths = 24, serviceCenterPhone = "18003159999"))
        repo.saveProduct(Product(name = "Bosch Front Load Washer", brand = "Bosch", modelNo = "WAJ2416WIN", serialNo = "FD9906 00412",
            purchaseDate = d(420), warrantyMonths = 24, extendedWarrantyMonths = 24))
        repo.saveProduct(Product(name = "Apple iPhone 15", brand = "Apple", modelNo = "MTP03HN/A", serialNo = "F2LXK9J1QW",
            purchaseDate = d(390), warrantyMonths = 12))
        repo.saveProduct(Product(name = "Daikin 1.5T Split AC", brand = "Daikin", modelNo = "FTKF50UV16V", purchaseDate = d(200),
            warrantyMonths = 12, extendedWarrantyMonths = 48, serviceCenterPhone = "18001029300"))

        // Recurring bills
        listOf(
            RecurringBill(name = "Airtel Xstream Fiber", type = RecurringType.UTILITY, amountPaise = 999_00, nextDueDate = today.plusDays(3), categoryId = cat("Utilities")),
            RecurringBill(name = "BESCOM electricity", type = RecurringType.UTILITY, nextDueDate = today.plusDays(2), categoryId = cat("Utilities")),
            RecurringBill(name = "Netflix", type = RecurringType.SUBSCRIPTION, amountPaise = 649_00, nextDueDate = today.plusDays(9), autopay = true),
            RecurringBill(name = "Spotify Premium", type = RecurringType.SUBSCRIPTION, amountPaise = 119_00, nextDueDate = today.plusDays(14), autopay = true),
            RecurringBill(name = "HDFC car loan", type = RecurringType.EMI, amountPaise = 14_250_00, nextDueDate = today.plusDays(12), autopay = true),
            RecurringBill(name = "LIC Jeevan Anand", type = RecurringType.INSURANCE, amountPaise = 18_500_00, frequency = Frequency.YEARLY, nextDueDate = today.plusDays(52), categoryId = cat("Insurance")),
            RecurringBill(name = "Bike PUC certificate", type = RecurringType.DOCUMENT, amountPaise = 100_00, frequency = Frequency.HALF_YEARLY, nextDueDate = today.plusDays(37)),
        ).forEach { repo.saveRecurring(it) }
    }

    private fun merchantFor(category: String) = when (category) {
        "Rent" -> "House rent"
        "Groceries" -> "DMart"
        "Food & Dining" -> "Swiggy"
        "Fuel" -> "Indian Oil"
        "Utilities" -> "BESCOM electricity"
        else -> "Amazon"
    }

    /** Draws a printed-looking thermal receipt so bill screens have a real photo. */
    private fun receipt(
        context: Context, store: String, address: String, date: LocalDate, invoice: String,
        items: List<Pair<String, String>>, cgst: String, sgst: String, total: String,
    ): BillFile {
        val w = 900
        val h = 1300
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(250, 248, 242))
        val mono = Paint().apply { typeface = Typeface.MONOSPACE; textSize = 30f; color = Color.rgb(40, 40, 40); isAntiAlias = true }
        val bold = Paint(mono).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 56f; textAlign = Paint.Align.CENTER }
        val center = Paint(mono).apply { textAlign = Paint.Align.CENTER }
        val right = Paint(mono).apply { textAlign = Paint.Align.RIGHT }
        var y = 110f
        canvas.drawText(store, w / 2f, y, bold); y += 50
        canvas.drawText(address, w / 2f, y, center); y += 40
        canvas.drawText("GSTIN: 29AABCI1234F1Z5", w / 2f, y, center); y += 40
        canvas.drawText("TAX INVOICE", w / 2f, y, center); y += 60
        canvas.drawText("Invoice No: $invoice", 60f, y, mono); y += 40
        canvas.drawText("Date: ${date.dayOfMonth}/${date.monthValue}/${date.year}", 60f, y, mono); y += 30
        canvas.drawText("-".repeat(46), 60f, y, mono); y += 50
        items.forEach { (name, amount) ->
            canvas.drawText(name.take(30), 60f, y, mono); canvas.drawText(amount, w - 60f, y, right); y += 46
        }
        canvas.drawText("-".repeat(46), 60f, y, mono); y += 50
        canvas.drawText("CGST @ 9%", 60f, y, mono); canvas.drawText(cgst, w - 60f, y, right); y += 46
        canvas.drawText("SGST @ 9%", 60f, y, mono); canvas.drawText(sgst, w - 60f, y, right); y += 30
        canvas.drawText("=".repeat(46), 60f, y, mono); y += 60
        val big = Paint(mono).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 40f }
        canvas.drawText("GRAND TOTAL", 60f, y, big); canvas.drawText("Rs. $total", w - 60f, y, Paint(big).apply { textAlign = Paint.Align.RIGHT }); y += 90
        canvas.drawText("Thank you for shopping with us!", w / 2f, y, center)
        val dir = File(context.filesDir, "bills").apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return BillFile(billId = 0, path = out.absolutePath, mimeType = "image/jpeg")
    }
}
