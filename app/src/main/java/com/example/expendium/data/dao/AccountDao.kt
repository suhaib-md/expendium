// In data/dao/AccountDao.kt
package com.example.expendium.data.dao // Ensure this package is correct

import androidx.room.*
import com.example.expendium.data.model.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account) // Correct: This is what TransactionRepository will call

    @Delete
    suspend fun deleteAccount(account: Account)

    // Use this for non-Flow synchronous access if needed within a transaction or for immediate data
    @Query("SELECT * FROM accounts WHERE accountId = :accountId")
    suspend fun getAccountByIdStatic(accountId: Long?): Account? // Made accountId nullable to match getAccountById

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE accountId = :accountId")
    fun getAccountById(accountId: Long?): Flow<Account?>
}