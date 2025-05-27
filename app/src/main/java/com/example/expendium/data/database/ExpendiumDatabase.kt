package com.example.expendium.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.expendium.data.dao.TransactionDao
import com.example.expendium.data.dao.CategoryDao
import com.example.expendium.data.dao.BudgetDao
import com.example.expendium.data.dao.AccountDao
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.Budget
import com.example.expendium.data.model.Account

@Database(
    entities = [Transaction::class, Category::class, Budget::class, Account::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ExpendiumDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun accountDao(): AccountDao

    companion object {
        const val DATABASE_NAME = "expendium_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the new accountNumber column to the accounts table
                // Make it TEXT and allow NULL values initially
                db.execSQL("ALTER TABLE accounts ADD COLUMN accountNumber TEXT")
            }
        }
    }
}