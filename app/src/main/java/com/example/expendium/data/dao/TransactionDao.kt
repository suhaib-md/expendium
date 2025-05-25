// data/dao/TransactionDao.kt
package com.example.expendium.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId")
    fun getTransactionById(transactionId: Long?): Flow<Transaction?> // Can be nullable if ID not found

    @Query("SELECT * FROM transactions WHERE transactionDate BETWEEN :startDate AND :endDate ORDER BY transactionDate DESC")
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY transactionDate DESC")
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY transactionDate DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE merchantOrPayee LIKE '%' || :searchQuery || '%' OR notes LIKE '%' || :searchQuery || '%' ORDER BY transactionDate DESC")
    fun searchTransactions(searchQuery: String): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND transactionDate BETWEEN :startDate AND :endDate")
    suspend fun getTotalAmountByTypeAndDateRange(type: TransactionType, startDate: Long, endDate: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE categoryId = :categoryId AND transactionDate BETWEEN :startDate AND :endDate")
    suspend fun getTotalAmountByCategoryAndDateRange(categoryId: Long, startDate: Long, endDate: Long): Double?

    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE transactionId = :id")
    suspend fun deleteTransactionById(id: Long)
}



