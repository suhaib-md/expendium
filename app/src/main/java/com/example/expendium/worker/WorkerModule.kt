package com.example.expendium.worker

import android.content.Context
import com.example.expendium.data.repository.AccountRepository
import com.example.expendium.data.repository.TransactionRepository
import com.example.expendium.data.repository.CategoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    @Provides
    @Singleton
    fun provideSmsValidator(): SmsValidator {
        return SmsValidator()
    }

    @Provides
    @Singleton
    fun provideTransactionParser(): TransactionParser {
        return TransactionParser()
    }

    @Provides
    @Singleton
    fun provideAccountResolver(
        @ApplicationContext context: Context,
        accountRepository: AccountRepository,
        transactionRepository: TransactionRepository
    ): AccountResolver {
        return AccountResolver(context, accountRepository, transactionRepository)
    }

    @Provides
    @Singleton
    fun provideCategoryResolver(categoryRepository: CategoryRepository): CategoryResolver {
        return CategoryResolver(categoryRepository)
    }

    @Provides
    @Singleton
    fun provideDuplicateDetector(@ApplicationContext context: Context): DuplicateDetector {
        return DuplicateDetector(context)
    }
}