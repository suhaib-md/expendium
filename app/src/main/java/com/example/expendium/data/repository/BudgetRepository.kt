// data/repository/BudgetRepository.kt
package com.example.expendium.data.repository

import kotlinx.coroutines.flow.Flow
import com.example.expendium.data.dao.BudgetDao
import com.example.expendium.data.model.Budget
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao
) {

    fun getAllBudgets(): Flow<List<Budget>> = budgetDao.getAllBudgets()

    suspend fun getBudgetById(id: Long): Budget? = budgetDao.getBudgetById(id)

    suspend fun getActiveBudgetForCategory(categoryId: Long, currentDate: Long): Budget? =
        budgetDao.getActiveBudgetForCategory(categoryId, currentDate)

    suspend fun getActiveOverallBudget(currentDate: Long): Budget? =
        budgetDao.getActiveOverallBudget(currentDate)

    suspend fun insertBudget(budget: Budget): Long = budgetDao.insertBudget(budget)

    suspend fun updateBudget(budget: Budget) = budgetDao.updateBudget(budget)

    suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)

    suspend fun deleteBudgetById(id: Long) = budgetDao.deleteBudgetById(id)
}