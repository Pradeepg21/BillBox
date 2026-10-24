package com.billbox.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isSystem DESC, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Upsert
    suspend fun upsert(category: Category): Long

    @Delete
    suspend fun delete(category: Category)
}

@Dao
interface BillDao {
    @Transaction
    @Query(
        """
        SELECT * FROM bills
        WHERE :q = '' OR merchant LIKE '%' || :q || '%' OR notes LIKE '%' || :q || '%'
           OR tags LIKE '%' || :q || '%' OR invoiceNo LIKE '%' || :q || '%'
           OR rawOcrText LIKE '%' || :q || '%'
        ORDER BY billDate DESC, id DESC
        """
    )
    fun search(q: String): Flow<List<BillWithExtras>>

    @Query("SELECT * FROM bills ORDER BY billDate DESC, id DESC")
    fun observeAll(): Flow<List<Bill>>

    @Query("SELECT * FROM bills")
    suspend fun all(): List<Bill>

    @Transaction
    @Query("SELECT * FROM bills ORDER BY billDate DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BillWithExtras>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id")
    fun observe(id: Long): Flow<BillWithExtras?>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun get(id: Long): BillWithExtras?

    @Transaction
    @Query("SELECT * FROM bills WHERE billDate BETWEEN :fromEpochDay AND :toEpochDay ORDER BY billDate DESC, id DESC")
    suspend fun between(fromEpochDay: Long, toEpochDay: Long): List<BillWithExtras>

    @Query(
        """
        SELECT * FROM bills WHERE id != :exceptId AND billDate = :epochDay AND totalPaise = :totalPaise
          AND LOWER(merchant) = LOWER(:merchant) LIMIT 1
        """
    )
    suspend fun findDuplicate(exceptId: Long, merchant: String, epochDay: Long, totalPaise: Long): Bill?

    @Insert
    suspend fun insert(bill: Bill): Long

    @Update
    suspend fun update(bill: Bill)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertFiles(files: List<BillFile>)

    @Query("SELECT * FROM bill_files WHERE billId = :billId")
    suspend fun files(billId: Long): List<BillFile>

    @Query("DELETE FROM bill_files WHERE id = :id")
    suspend fun deleteFile(id: Long)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY purchaseDate DESC")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products")
    suspend fun all(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: Long): Product?

    @Query("SELECT * FROM products WHERE billId = :billId")
    fun observeForBill(billId: Long): Flow<List<Product>>

    @Upsert
    suspend fun upsert(product: Product): Long

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM service_records WHERE productId = :productId ORDER BY date DESC")
    fun observeService(productId: Long): Flow<List<ServiceRecord>>

    @Insert
    suspend fun insertService(record: ServiceRecord)

    @Delete
    suspend fun deleteService(record: ServiceRecord)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_bills ORDER BY isActive DESC, nextDueDate")
    fun observeAll(): Flow<List<RecurringBill>>

    @Query("SELECT * FROM recurring_bills")
    suspend fun all(): List<RecurringBill>

    @Query("SELECT * FROM recurring_bills WHERE id = :id")
    suspend fun get(id: Long): RecurringBill?

    @Upsert
    suspend fun upsert(item: RecurringBill): Long

    @Query("DELETE FROM recurring_bills WHERE id = :id")
    suspend fun delete(id: Long)
}
