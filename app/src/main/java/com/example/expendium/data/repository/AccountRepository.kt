// In data/repository/AccountRepository.kt
package com.example.expendium.data.repository

import com.example.expendium.data.dao.AccountDao
import com.example.expendium.data.model.Account
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface AccountRepository {
    fun getAllAccounts(): Flow<List<Account>>
    fun getAccountById(accountId: Long?): Flow<Account?>
    suspend fun getAccountByIdStatic(accountId: Long?): Account? // Added for non-flow access
    suspend fun insertAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun deleteAccount(account: Account)
}

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao
) : AccountRepository {
    override fun getAllAccounts(): Flow<List<Account>> = accountDao.getAllAccounts()
    override fun getAccountById(accountId: Long?): Flow<Account?> = accountDao.getAccountById(accountId)
    override suspend fun getAccountByIdStatic(accountId: Long?): Account? = accountDao.getAccountByIdStatic(accountId) // Implement
    override suspend fun insertAccount(account: Account): Long = accountDao.insertAccount(account)
    override suspend fun updateAccount(account: Account) = accountDao.updateAccount(account)
    override suspend fun deleteAccount(account: Account) = accountDao.deleteAccount(account)
}