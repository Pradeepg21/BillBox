package com.billbox.app.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.billbox.app.data.BillWithExtras
import com.billbox.app.util.Dates
import com.billbox.app.util.FileStore
import com.billbox.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Writes CSV and PDF reports into the cache dir, ready to share. */
class Exporter(private val context: Context, private val files: FileStore) {

    private val outDir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun csv(bills: List<BillWithExtras>, name: String): File = withContext(Dispatchers.IO) {
        val out = File(outDir, "$name.csv")
        out.bufferedWriter().use { w ->
            w.appendLine("Date,Merchant,Category,Amount (INR),Tax (INR),Payment,Invoice no,GSTIN,Tax section,Business,Tags,Notes")
            for (x in bills) {
                val bill = x.bill
                w.appendLine(listOf(
                    bill.billDate.toString(), bill.merchant, x.category?.name ?: "",
                    Money.toInput(bill.totalPaise), Money.toInput(bill.taxPaise), bill.paymentMethod.label,
                    bill.invoiceNo ?: "", bill.gstin ?: "", bill.taxSection?.name ?: "",
                    if (bill.isBusiness) "yes" else "no", bill.tags, bill.notes,
                ).joinToString(",") { csvCell(it) })
            }
        }
        out
    }

    suspend fun pdf(bills: List<BillWithExtras>, title: String, name: String, includeImages: Boolean): File =
        withContext(Dispatchers.IO) {
            val doc = PdfDocument()
            val text = Paint().apply { textSize = 10f; isAntiAlias = true }
            val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
            val heading = Paint(bold).apply { textSize = 18f }
            val rule = Paint().apply { strokeWidth = 0.5f; color = 0xFFCBD5E1.toInt() }
            var pageNo = 0
            fun newPage() = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNo).create())

            // Summary pages: a table of every bill.
            var page = newPage()
            var y = MARGIN + 18f
            page.canvas.drawText(title, MARGIN, y, heading)
            y += 18f
            val total = bills.sumOf { it.bill.totalPaise }
            page.canvas.drawText(
                "${bills.size} bills · total ${Money.format(total)} · generated ${Dates.format(LocalDate.now())}",
                MARGIN, y, text,
            )
            y += 24f
            val cols = floatArrayOf(MARGIN, MARGIN + 70, MARGIN + 260, MARGIN + 370, MARGIN + 440)
            fun header() {
                listOf("Date", "Merchant", "Category", "Payment", "Amount").forEachIndexed { i, h ->
                    page.canvas.drawText(h, cols[i], y, bold)
                }
                y += 6f
                page.canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
                y += 14f
            }
            header()
            for (x in bills) {
                if (y > PAGE_H - MARGIN) {
                    doc.finishPage(page)
                    page = newPage()
                    y = MARGIN + 12f
                    header()
                }
                val b = x.bill
                page.canvas.drawText(Dates.format(b.billDate), cols[0], y, text)
                page.canvas.drawText(b.merchant.take(32), cols[1], y, text)
                page.canvas.drawText((x.category?.name ?: "").take(18), cols[2], y, text)
                page.canvas.drawText(b.paymentMethod.label, cols[3], y, text)
                page.canvas.drawText(Money.format(b.totalPaise), cols[4], y, text)
                y += 16f
            }
            y += 4f
            page.canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
            y += 16f
            page.canvas.drawText("Total", cols[3], y, bold)
            page.canvas.drawText(Money.format(total), cols[4], y, bold)
            doc.finishPage(page)

            // One page per bill image, as proof for reimbursement or tax.
            if (includeImages) {
                for (x in bills) for (f in x.files.filter { it.isImage }.sortedBy { it.pageNo }) {
                    val bmp = BitmapFactory.decodeFile(f.path) ?: continue
                    page = newPage()
                    page.canvas.drawText(
                        "${x.bill.merchant} · ${Dates.format(x.bill.billDate)} · ${Money.format(x.bill.totalPaise)}",
                        MARGIN, MARGIN + 10f, bold,
                    )
                    val boxW = PAGE_W - 2 * MARGIN
                    val boxH = PAGE_H - 2 * MARGIN - 24f
                    val scale = minOf(boxW / bmp.width, boxH / bmp.height)
                    val w = bmp.width * scale
                    val h = bmp.height * scale
                    val left = MARGIN + (boxW - w) / 2
                    val top = MARGIN + 24f
                    page.canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), null)
                    bmp.recycle()
                    doc.finishPage(page)
                }
            }
            val out = File(outDir, "$name.pdf")
            out.outputStream().use { doc.writeTo(it) }
            doc.close()
            out
        }

    fun share(file: File, subject: String) =
        files.shareIntent(listOf(file), if (file.extension == "pdf") "application/pdf" else "text/csv", subject)

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    companion object {
        private const val PAGE_W = 595 // A4 in points
        private const val PAGE_H = 842
        private const val MARGIN = 40f
    }
}
