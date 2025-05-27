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

    // Adding a non-Flow version to get a transaction by ID, useful in repositories
    @Query("SELECT * FROM transactions WHERE transactionId = :transactionId")
    suspend fun getTransactionByIdStatic(transactionId: Long?): Transaction?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    // NEW METHODS FOR TIME RANGE QUERIES AND DUPLICATE DETECTION

    /**
     * Get transactions within a specific time range for duplicate detection
     */
    @Query("""
        SELECT * FROM transactions 
        WHERE transactionDate >= :startTime AND transactionDate <= :endTime 
        ORDER BY transactionDate DESC
    """)
    suspend fun getTransactionsInTimeRange(startTime: Long, endTime: Long): List<Transaction>

    /**
     * Find a similar transaction based on amount, type, and time window
     */
    @Query("""
        SELECT * FROM transactions 
        WHERE amount BETWEEN :minAmount AND :maxAmount 
        AND type = :type 
        AND transactionDate BETWEEN :startTime AND :endTime 
        ORDER BY transactionDate DESC 
        LIMIT 1
    """)
    suspend fun findSimilarTransaction(
        minAmount: Double,
        maxAmount: Double,
        type: TransactionType,
        startTime: Long,
        endTime: Long
    ): Transaction?

    @Query("""
    SELECT * FROM transactions 
    WHERE notes LIKE '%' || :sender || '%' 
    AND isManual = 0 
    ORDER BY transactionDate DESC 
    LIMIT :limit
""")
    suspend fun getRecentTransactionsBySender(sender: String, limit: Int): List<Transaction>
}