package com.example.expendium.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.example.expendium.data.dao.TransactionDao
import com.example.expendium.data.dao.CategoryDao
import com.example.expendium.data.dao.BudgetDao
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.Budget

@Database(
    entities = [Transaction::class, Category::class, Budget::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ExpendiumDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DATABASE_NAME = "expendium_database"
    }
}