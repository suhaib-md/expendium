package com.example.expendium.data.repository

import com.example.expendium.data.DefaultDataProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataInitializer @Inject constructor(
    private val categoryRepository: CategoryRepository
) {

    suspend fun initializeDefaultData() {
        try {
            val defaultCategories = DefaultDataProvider.getDefaultCategories()

            // Insert categories only if they don't exist
            defaultCategories.forEach { category ->
                try {
                    categoryRepository.insertCategory(category)
                } catch (e: Exception) {
                    // Category might already exist, ignore the exception
                    // In a production app, you might want to check if category exists first
                }
            }
        } catch (e: Exception) {
            // Handle initialization error
            e.printStackTrace()
        }
    }
}