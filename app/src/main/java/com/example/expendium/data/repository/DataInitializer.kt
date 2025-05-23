// data/repository/DataInitializer.kt
package com.example.expendium.data.repository

import com.example.expendium.data.DefaultDataProvider
import com.example.expendium.data.model.Category
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataInitializer @Inject constructor(
    private val categoryRepository: CategoryRepository
) {

    suspend fun initializeDefaultData() {
        try {
            val existingCategories = categoryRepository.getAllCategories().firstOrNull() ?: emptyList()
            val defaultCategories = DefaultDataProvider.getDefaultCategories()

            val categoriesToInsert = defaultCategories.filter { defaultCategory ->
                // Check if a category with the same name and type already exists
                existingCategories.none { existing ->
                    existing.name == defaultCategory.name && existing.type == defaultCategory.type
                }
            }

            if (categoriesToInsert.isNotEmpty()) {
                categoriesToInsert.forEach { category ->
                    // Make sure categoryId is 0 for Room to auto-generate
                    categoryRepository.insertCategory(category.copy(categoryId = 0))
                }
                println("DataInitializer: Added ${categoriesToInsert.size} new default categories.")
            } else {
                println("DataInitializer: Default categories already exist or no new ones to add.")
            }

        } catch (e: Exception) {
            // Log or handle initialization error more gracefully
            println("DataInitializer: Error initializing default data: ${e.message}")
            e.printStackTrace()
        }
    }
}