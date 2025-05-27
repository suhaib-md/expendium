// data/repository/TransactionRepository.kt
package com.example.expendium.data.repository

import kotlinx.coroutines.flow.Flow
import com.example.expendium.data.dao.TransactionDao
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
import com.example.expendium.data.dao.AccountDao
import com.example.expendium.data.model.Account
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao
) {

    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getTransactionById(transactionId: Long?): Flow<Transaction?> {
        return transactionDao.getTransactionById(transactionId)
    }

    // Add a non-flow version to get Transaction for internal logic
    suspend fun getTransactionByIdStatic(transactionId: Long?): Transaction? {
        return transactionDao.getTransactionByIdStatic(transactionId)
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

    // These methods now just call the simple DAO methods
    suspend fun insertTransaction(transaction: Transaction): Long = transactionDao.insert(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.delete(transaction)
    suspend fun deleteTransactionById(id: Long) = transactionDao.deleteTransactionById(id)


    // --- Combined Operations ---
    // These methods will now call TransactionDao and AccountDao sequentially.

    suspend fun insertTransactionAndUpdateAccountBalance(transaction: Transaction, currentAccountState: Account) {
        val newBalance = if (transaction.type == TransactionType.EXPENSE) {
            currentAccountState.currentBalance - transaction.amount
        } else {
            currentAccountState.currentBalance + transaction.amount
        }
        // Ensure you are using the correct accountId from the currentAccountState
        val updatedAccount = currentAccountState.copy(
            currentBalance = newBalance,
            updatedAt = System.currentTimeMillis()
        )

        transactionDao.insert(transaction) // Call the simple insert
        accountDao.updateAccount(updatedAccount) // Call AccountDao's update
    }

    suspend fun updateTransactionAndUpdateAccountBalance(
        updatedTransaction: Transaction,
        accountBeforeUpdate: Account, // The state of the account BEFORE this transaction was modified
        oldTransactionAmount: Double?,
        oldTransactionType: TransactionType?
    ) {
        var newAccountBalance = accountBeforeUpdate.currentBalance

        // 1. Revert the old transaction's effect from the account's balance
        if (oldTransactionAmount != null && oldTransactionType != null) {
            newAccountBalance = if (oldTransactionType == TransactionType.EXPENSE) {
                newAccountBalance + oldTransactionAmount
            } else {
                newAccountBalance - oldTransactionAmount
            }
        }

        // 2. Apply the new transaction's effect to the (now reverted) balance
        newAccountBalance = if (updatedTransaction.type == TransactionType.EXPENSE) {
            newAccountBalance - updatedTransaction.amount
        } else {
            newAccountBalance + updatedTransaction.amount
        }

        val finalUpdatedAccount = accountBeforeUpdate.copy(
            currentBalance = newAccountBalance,
            updatedAt = System.currentTimeMillis()
        )

        transactionDao.update(updatedTransaction) // Call the simple update
        accountDao.updateAccount(finalUpdatedAccount) // Call AccountDao's update
    }

    suspend fun deleteTransactionAndUpdateAccountBalance(transactionToDelete: Transaction, currentAccountState: Account) {
        val newBalance = if (transactionToDelete.type == TransactionType.EXPENSE) {
            currentAccountState.currentBalance + transactionToDelete.amount
        } else {
            currentAccountState.currentBalance - transactionToDelete.amount
        }
        val updatedAccount = currentAccountState.copy(
            currentBalance = newBalance,
            updatedAt = System.currentTimeMillis()
        )

        transactionDao.delete(transactionToDelete) // Call the simple delete
        accountDao.updateAccount(updatedAccount) // Call AccountDao's update
    }
}