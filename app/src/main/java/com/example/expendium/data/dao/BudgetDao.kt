// data/dao/BudgetDao.kt
package com.example.expendium.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.expendium.data.model.Budget

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets ORDER BY startDate DESC")
    fun getAllBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE budgetId = :id")
    suspend fun getBudgetById(id: Long): Budget?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND startDate <= :currentDate AND endDate >= :currentDate")
    suspend fun getActiveBudgetForCategory(categoryId: Long, currentDate: Long): Budget?

    @Query("SELECT * FROM budgets WHERE categoryId IS NULL AND startDate <= :currentDate AND endDate >= :currentDate")
    suspend fun getActiveOverallBudget(currentDate: Long): Budget?

    @Insert
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE budgetId = :id")
    suspend fun deleteBudgetById(id: Long)
}