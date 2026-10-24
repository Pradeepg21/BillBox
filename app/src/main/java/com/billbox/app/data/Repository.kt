package com.billbox.app.data

import androidx.room.withTransaction
import com.billbox.app.util.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Single entry point the UI uses for all reads and writes. */
class Repository(private val db: AppDatabase, private val files: FileStore) {

    val categories: Flow<List<Category>> = db.categories().observeAll()
    val allBills: Flow<List<Bill>> = db.bills().observeAll()
    val products: Flow<List<Product>> = db.products().observeAll()
    val recurring: Flow<List<RecurringBill>> = db.recurring().observeAll()

    suspend fun seedIfEmpty() {
        if (db.categories().count() == 0) db.categories().insertAll(AppDatabase.defaultCategories)
    }

    // Bills

    fun searchBills(query: String): Flow<List<BillWithExtras>> = db.bills().search(query.trim())
    fun recentBills(limit: Int = 5): Flow<List<BillWithExtras>> = db.bills().observeRecent(limit)
    fun observeBill(id: Long): Flow<BillWithExtras?> = db.bills().observe(id)
    suspend fun getBill(id: Long): BillWithExtras? = db.bills().get(id)
    suspend fun billsBetween(from: LocalDate, to: LocalDate) = db.bills().between(from.toEpochDay(), to.toEpochDay())

    suspend fun findDuplicate(bill: Bill): Bill? =
        db.bills().findDuplicate(bill.id, bill.merchant.trim(), bill.billDate.toEpochDay(), bill.totalPaise)

    /**
     * Inserts or updates [bill]. [newFiles] are files already copied into app storage;
     * [removedFiles] are deleted from the database and from disk.
     */
    suspend fun saveBill(bill: Bill, newFiles: List<BillFile>, removedFiles: List<BillFile> = emptyList()): Long =
        db.withTransaction {
            val id = if (bill.id == 0L) {
                db.bills().insert(bill)
            } else {
                db.bills().update(bill.copy(updatedAt = System.currentTimeMillis()))
                bill.id
            }
            removedFiles.forEach { db.bills().deleteFile(it.id) }
            if (newFiles.isNotEmpty()) db.bills().insertFiles(newFiles.map { it.copy(id = 0, billId = id) })
            id
        }.also { removedFiles.forEach { File(it.path).delete() } }

    suspend fun deleteBill(id: Long) {
        val paths = db.bills().files(id).map { it.path }
        db.bills().delete(id)
        paths.forEach { File(it).delete() }
    }

    // Products and warranty

    fun observeProduct(id: Long) = db.products().observe(id)
    fun productsForBill(billId: Long) = db.products().observeForBill(billId)
    suspend fun getProduct(id: Long) = db.products().get(id)
    suspend fun saveProduct(product: Product): Long {
        val id = db.products().upsert(product)
        return if (product.id != 0L) product.id else id
    }
    suspend fun deleteProduct(id: Long) {
        db.products().get(id)?.warrantyCardPath?.let { File(it).delete() }
        db.products().delete(id)
    }
    fun serviceRecords(productId: Long) = db.products().observeService(productId)
    suspend fun addService(record: ServiceRecord) = db.products().insertService(record)
    suspend fun deleteService(record: ServiceRecord) = db.products().deleteService(record)

    // Categories

    suspend fun saveCategory(category: Category) = db.categories().upsert(category)
    suspend fun deleteCategory(category: Category) = db.categories().delete(category)

    // Recurring bills

    suspend fun getRecurring(id: Long) = db.recurring().get(id)
    suspend fun saveRecurring(item: RecurringBill) = db.recurring().upsert(item)
    suspend fun deleteRecurring(id: Long) = db.recurring().delete(id)

    /** Records a payment as a bill and moves the due date forward one period. */
    suspend fun markRecurringPaid(item: RecurringBill, amountPaise: Long, paidOn: LocalDate = LocalDate.now()) {
        db.withTransaction {
            db.bills().insert(
                Bill(
                    merchant = item.name,
                    billDate = paidOn,
                    totalPaise = amountPaise,
                    categoryId = item.categoryId,
                    notes = "${item.type.label} payment",
                    source = BillSource.RECURRING,
                    paymentMethod = PaymentMethod.UPI,
                )
            )
            db.recurring().upsert(item.copy(nextDueDate = item.nextDueDate.plusMonths(item.frequency.months)))
        }
    }

    // Snapshots for the reminder worker and exports

    suspend fun snapshot() = Snapshot(db.bills().all(), db.products().all(), db.recurring().all(), db.categories().all())

    suspend fun deleteEverything() {
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            files.deleteAll()
        }
        seedIfEmpty()
    }

    data class Snapshot(
        val bills: List<Bill>,
        val products: List<Product>,
        val recurring: List<RecurringBill>,
        val categories: List<Category>,
    )
}
