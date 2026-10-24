package com.billbox.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

class Converters {
    @TypeConverter fun fromDate(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun toDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}

@Database(
    entities = [Category::class, Bill::class, BillFile::class, Product::class, ServiceRecord::class, RecurringBill::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun bills(): BillDao
    abstract fun products(): ProductDao
    abstract fun recurring(): RecurringDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "billbox.db").build()

        val defaultCategories = listOf(
            Category(name = "Electronics", icon = "devices", color = 0xFF3B82F6, isSystem = true),
            Category(name = "Groceries", icon = "cart", color = 0xFF16A34A, isSystem = true),
            Category(name = "Food & Dining", icon = "food", color = 0xFFF97316, isSystem = true),
            Category(name = "Medical", icon = "medical", color = 0xFFDC2626, isSystem = true),
            Category(name = "Utilities", icon = "bolt", color = 0xFFEAB308, isSystem = true),
            Category(name = "Fuel", icon = "fuel", color = 0xFF78716C, isSystem = true),
            Category(name = "Travel", icon = "flight", color = 0xFF0EA5E9, isSystem = true),
            Category(name = "Shopping", icon = "bag", color = 0xFFDB2777, isSystem = true),
            Category(name = "Education", icon = "school", color = 0xFF7C3AED, isSystem = true),
            Category(name = "Insurance", icon = "shield", color = 0xFF0F766E, isSystem = true),
            Category(name = "Rent", icon = "home", color = 0xFF9333EA, isSystem = true),
            Category(name = "Other", icon = "receipt", color = 0xFF64748B, isSystem = true),
        )
    }
}
