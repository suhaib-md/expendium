// data/dao/CategoryDao.kt
package com.example.expendium.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.TransactionType

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY name ASC")
    fun getCategoriesByType(type: TransactionType): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE categoryId = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Insert
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("DELETE FROM categories WHERE categoryId = :id")
    suspend fun deleteCategoryById(id: Long)
}