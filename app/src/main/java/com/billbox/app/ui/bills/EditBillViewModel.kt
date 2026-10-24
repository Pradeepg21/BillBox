package com.billbox.app.ui.bills

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billbox.app.AppContainer
import com.billbox.app.data.Bill
import com.billbox.app.data.BillFile
import com.billbox.app.data.BillSource
import com.billbox.app.data.Category
import com.billbox.app.data.PaymentMethod
import com.billbox.app.data.TaxSection
import com.billbox.app.ocr.ReceiptParser
import com.billbox.app.reminders.ReminderWorker
import com.billbox.app.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class EditBillViewModel(private val c: AppContainer, private val billId: Long) : ViewModel() {

    val categories: StateFlow<List<Category>> =
        c.repository.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var merchant by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var total by mutableStateOf("")
    var tax by mutableStateOf("")
    var gstin by mutableStateOf("")
    var invoiceNo by mutableStateOf("")
    var payment by mutableStateOf(PaymentMethod.UPI)
    var categoryId by mutableStateOf<Long?>(null)
    var notes by mutableStateOf("")
    var tags by mutableStateOf("")
    var isBusiness by mutableStateOf(false)
    var taxSection by mutableStateOf<TaxSection?>(null)
    var returnBy by mutableStateOf<LocalDate?>(null)
    var trackWarranty by mutableStateOf(false)

    /** Saved files (when editing) plus files imported in this session. */
    var files by mutableStateOf<List<BillFile>>(emptyList())
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var duplicateOf by mutableStateOf<Bill?>(null)
    var ocrFilled by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(billId == 0L)
        private set

    private var original: Bill? = null
    private var rawOcr = ""
    private var source = BillSource.MANUAL
    private val removed = mutableListOf<BillFile>()
    private var saved = false

    val isNew get() = billId == 0L
    val totalError get() = total.isNotBlank() && Money.parse(total) == null

    init {
        if (billId != 0L) viewModelScope.launch {
            c.repository.getBill(billId)?.let { x ->
                val b = x.bill
                original = b
                merchant = b.merchant; date = b.billDate; total = Money.toInput(b.totalPaise)
                tax = if (b.taxPaise > 0) Money.toInput(b.taxPaise) else ""
                gstin = b.gstin ?: ""; invoiceNo = b.invoiceNo ?: ""; payment = b.paymentMethod
                categoryId = b.categoryId; notes = b.notes; tags = b.tags; isBusiness = b.isBusiness
                taxSection = b.taxSection; returnBy = b.returnByDate; rawOcr = b.rawOcrText; source = b.source
                files = x.files.sortedBy { it.pageNo }
            }
            loaded = true
        }
    }

    fun addUris(uris: List<Uri>, from: BillSource) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy = "Saving pages…"
            try {
                val start = files.count { it.isImage }
                val imported = uris.flatMapIndexed { i, uri -> c.files.import(uri, start + i) }
                files = files + imported
                if (source == BillSource.MANUAL) source = from
                runOcr(imported.filter { it.isImage }.map { it.path })
            } catch (e: Exception) {
                message = "Could not read that file: ${e.message}"
            } finally {
                busy = null
            }
        }
    }

    fun removeFile(file: BillFile) {
        drop(file)
        // Once the last preview page is gone, the original PDF goes with it.
        if (files.none { it.isImage }) files.filter { it.mimeType == "application/pdf" }.forEach(::drop)
    }

    private fun drop(file: BillFile) {
        files = files - file
        if (file.id != 0L) removed += file else File(file.path).delete()
    }

    private suspend fun runOcr(paths: List<String>) {
        if (paths.isEmpty()) return
        busy = "Reading the bill…"
        val text = try {
            c.ocr.read(paths)
        } catch (e: Exception) {
            message = "Text recognition is not available yet. Fill the details manually."
            return
        }
        rawOcr = listOf(rawOcr, text).filter { it.isNotBlank() }.joinToString("\n")
        val p = ReceiptParser.parse(text)
        // Only fill what the user has not typed yet.
        if (merchant.isBlank()) p.merchant?.let { merchant = it }
        if (isNew && p.date != null) date = p.date
        if (total.isBlank()) p.totalPaise?.let { total = Money.toInput(it) }
        if (tax.isBlank()) p.taxPaise?.takeIf { it > 0 }?.let { tax = Money.toInput(it) }
        if (gstin.isBlank()) p.gstin?.let { gstin = it }
        if (invoiceNo.isBlank()) p.invoiceNo?.let { invoiceNo = it }
        if (categoryId == null) {
            val cats = categories.value.ifEmpty { c.repository.categories.first() }
            categoryId = cats.firstOrNull { it.name == p.suggestedCategory }?.id
        }
        if (p.suggestedCategory == "Electronics") trackWarranty = true
        ocrFilled = p.totalPaise != null || p.merchant != null
        message = if (ocrFilled) "Details filled from the bill. Please check them." else "Couldn't read details. Please fill them in."
    }

    private fun build(): Bill? {
        val amount = Money.parse(total) ?: return null
        val base = original ?: Bill(merchant = "", billDate = date, totalPaise = 0)
        return base.copy(
            merchant = merchant.trim().ifEmpty { "Unnamed bill" },
            billDate = date,
            totalPaise = amount,
            taxPaise = Money.parse(tax) ?: 0,
            gstin = gstin.trim().uppercase().ifEmpty { null },
            invoiceNo = invoiceNo.trim().ifEmpty { null },
            paymentMethod = payment,
            categoryId = categoryId,
            notes = notes.trim(),
            tags = tags.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString(","),
            isBusiness = isBusiness,
            taxSection = taxSection,
            returnByDate = returnBy,
            rawOcrText = rawOcr,
            source = source,
        )
    }

    /** Returns the saved bill id, or null when validation failed or a duplicate needs confirming. */
    suspend fun save(ignoreDuplicate: Boolean = false): Long? {
        val bill = build() ?: run { message = "Enter the bill amount"; return null }
        if (!ignoreDuplicate) {
            c.repository.findDuplicate(bill)?.let { duplicateOf = it; return null }
        }
        val id = c.repository.saveBill(bill, files.filter { it.id == 0L }, removed)
        saved = true
        if (bill.returnByDate != null) ReminderWorker.runNow(c.app)
        return id
    }

    suspend fun delete() {
        if (billId != 0L) c.repository.deleteBill(billId)
        saved = true
    }

    override fun onCleared() {
        // Discarded without saving: remove the pages copied in this session.
        if (!saved) files.filter { it.id == 0L }.forEach { File(it.path).delete() }
    }
}
