package com.billbox.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

// Money is stored as whole paise (₹1 = 100 paise) so sums never drift.

enum class PaymentMethod(val label: String) {
    CASH("Cash"), UPI("UPI"), CARD("Card"), NET_BANKING("Net banking"), OTHER("Other")
}

enum class BillSource { CAMERA, GALLERY, PDF, MANUAL, SHARE, RECURRING }

/** Income-tax sections a bill can count towards (Indian financial year, April–March). */
enum class TaxSection(val label: String) {
    S80C("80C · investments, tuition"),
    S80D("80D · health insurance, medical"),
    S80G("80G · donations"),
    HRA("HRA · rent"),
    LTA("LTA · travel"),
}

enum class RecurringType(val label: String) {
    UTILITY("Utility"), SUBSCRIPTION("Subscription"), INSURANCE("Insurance"), EMI("EMI / loan"),
    RENT("Rent"), DOCUMENT("Document renewal"), OTHER("Other")
}

enum class Frequency(val label: String, val months: Long) {
    MONTHLY("Monthly", 1), QUARTERLY("Quarterly", 3), HALF_YEARLY("Every 6 months", 6), YEARLY("Yearly", 12)
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Key into [com.billbox.app.ui.components.categoryIcon]. */
    val icon: String,
    val color: Long,
    val monthlyBudgetPaise: Long? = null,
    val isSystem: Boolean = false,
)

@Entity(
    tableName = "bills",
    foreignKeys = [ForeignKey(
        entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("billDate"), Index("categoryId")],
)
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val billDate: LocalDate,
    val totalPaise: Long,
    val taxPaise: Long = 0,
    val gstin: String? = null,
    val invoiceNo: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val categoryId: Long? = null,
    val notes: String = "",
    /** Comma-separated, lower-case tags. */
    val tags: String = "",
    val isBusiness: Boolean = false,
    val taxSection: TaxSection? = null,
    val returnByDate: LocalDate? = null,
    val rawOcrText: String = "",
    val source: BillSource = BillSource.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "bill_files",
    foreignKeys = [ForeignKey(
        entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("billId")],
)
data class BillFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    /** Absolute path inside the app's private files dir. */
    val path: String,
    val mimeType: String,
    val pageNo: Int = 0,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(
        entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("billId")],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long? = null,
    val name: String,
    val brand: String = "",
    val modelNo: String = "",
    val serialNo: String = "",
    val purchaseDate: LocalDate,
    val warrantyMonths: Int,
    /** Extended warranty or AMC, counted after the standard warranty ends. */
    val extendedWarrantyMonths: Int = 0,
    val warrantyCardPath: String? = null,
    val serviceCenterPhone: String = "",
    val notes: String = "",
) {
    val warrantyExpiry: LocalDate get() = purchaseDate.plusMonths((warrantyMonths + extendedWarrantyMonths).toLong())
}

@Entity(
    tableName = "service_records",
    foreignKeys = [ForeignKey(
        entity = Product::class, parentColumns = ["id"], childColumns = ["productId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("productId")],
)
data class ServiceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val date: LocalDate,
    val issue: String,
    val costPaise: Long = 0,
    val serviceCenter: String = "",
)

@Entity(
    tableName = "recurring_bills",
    foreignKeys = [ForeignKey(
        entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("categoryId")],
)
data class RecurringBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: RecurringType,
    /** Null when the amount varies (electricity, water). */
    val amountPaise: Long? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val nextDueDate: LocalDate,
    val remindDaysBefore: Int = 3,
    val autopay: Boolean = false,
    val categoryId: Long? = null,
    val isActive: Boolean = true,
)

data class BillWithExtras(
    @Embedded val bill: Bill,
    @Relation(parentColumn = "categoryId", entityColumn = "id") val category: Category?,
    @Relation(parentColumn = "id", entityColumn = "billId") val files: List<BillFile>,
) {
    val thumbnail: BillFile? get() = files.filter { it.isImage }.minByOrNull { it.pageNo }
}
