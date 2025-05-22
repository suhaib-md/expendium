package com.example.expendium.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.example.expendium.data.database.ExpendiumDatabase
import com.example.expendium.data.dao.TransactionDao
import com.example.expendium.data.dao.CategoryDao
import com.example.expendium.data.dao.BudgetDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideExpendiumDatabase(@ApplicationContext context: Context): ExpendiumDatabase {
        return Room.databaseBuilder(
            context,
            ExpendiumDatabase::class.java,
            ExpendiumDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // Remove this in production
            .build()
    }

    @Provides
    fun provideTransactionDao(database: ExpendiumDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    fun provideCategoryDao(database: ExpendiumDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideBudgetDao(database: ExpendiumDatabase): BudgetDao {
        return database.budgetDao()
    }
}