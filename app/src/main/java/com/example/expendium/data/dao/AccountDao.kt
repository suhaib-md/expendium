// In data/local/dao/AccountDao.kt
package com.example.expendium.data.dao

import androidx.room.*
import com.example.expendium.data.model.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE accountId = :accountId")
    fun getAccountById(accountId: Long): Flow<Account?> // Or suspend fun if not Flow
}