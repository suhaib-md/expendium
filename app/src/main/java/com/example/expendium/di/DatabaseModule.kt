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
import com.example.expendium.data.dao.AccountDao
import com.example.expendium.data.repository.AccountRepository // Interface
import com.example.expendium.data.repository.AccountRepositoryImpl // Implementation
import dagger.Binds
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

    @Provides
    fun provideAccountDao(database: ExpendiumDatabase): AccountDao {
        return database.accountDao()
    }
}

// You can create a new module or add to an existing one
@Module
@InstallIn(SingletonComponent::class) // Or ActivityRetainedComponent::class if you want it scoped to ViewModel
abstract class RepositoryModule { // Note: abstract class for @Binds

    @Binds
    @Singleton // Or @ActivityRetainedScoped if matching the component
    abstract fun bindAccountRepository(
        accountRepositoryImpl: AccountRepositoryImpl
    ): AccountRepository
}