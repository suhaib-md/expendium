// data/repository/TransactionRepository.kt
package com.example.expendium.data.repository

import kotlinx.coroutines.flow.Flow
import com.example.expendium.data.dao.TransactionDao
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {

    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getTransactionById(transactionId: Long?): Flow<Transaction?> {
        return transactionDao.getTransactionById(transactionId)
    }
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByDateRange(startDate, endDate)

    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(categoryId)

    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> =
        transactionDao.getTransactionsByType(type)

    fun searchTransactions(query: String): Flow<List<Transaction>> =
        transactionDao.searchTransactions(query)

    suspend fun getTotalAmountByTypeAndDateRange(type: TransactionType, startDate: Long, endDate: Long): Double =
        transactionDao.getTotalAmountByTypeAndDateRange(type, startDate, endDate) ?: 0.0

    suspend fun getTotalAmountByCategoryAndDateRange(categoryId: Long, startDate: Long, endDate: Long): Double =
        transactionDao.getTotalAmountByCategoryAndDateRange(categoryId, startDate, endDate) ?: 0.0

    suspend fun insertTransaction(transaction: Transaction): Long = transactionDao.insertTransaction(transaction)

    suspend fun updateTransaction(transaction: Transaction) = transactionDao.updateTransaction(transaction)

    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.deleteTransaction(transaction)

    suspend fun deleteTransactionById(id: Long) = transactionDao.deleteTransactionById(id)
}