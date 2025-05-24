// ui/viewmodel/CategoryViewModel.kt
package com.example.expendium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expendium.data.model.Category
import com.example.expendium.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categories: List<Category> = emptyList() // Will be populated by the flow
)

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    // Flow to observe categories from the repository
    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addCategory(name: String, type: com.example.expendium.data.model.TransactionType, iconName: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                if (name.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Category name cannot be empty.") }
                    return@launch
                }
                // Check for duplicates (optional but good practice)
                val existingCategory = categoryRepository.getCategoryByNameAndType(name, type).firstOrNull()
                if (existingCategory != null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Category '$name' already exists for this type.") }
                    return@launch
                }

                val newCategory = Category(name = name, type = type, iconName = iconName) // categoryId will be auto-generated
                categoryRepository.insertCategory(newCategory)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to add category") }
            }
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                if (category.name.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Category name cannot be empty.") }
                    return@launch
                }
                // Optional: Check if the new name and type would conflict with another existing category
                // This logic can be complex if you allow changing the type. For simplicity,
                // you might prevent type changes or handle conflicts carefully.

                categoryRepository.updateCategory(category.copy(updatedAt = System.currentTimeMillis()))
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to update category") }
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Future consideration: Check if category is in use by transactions
                // If so, either prevent deletion, reassign transactions, or warn user.
                categoryRepository.deleteCategoryById(categoryId)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to delete category") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Helper to set a user-facing error if needed from the UI
    fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
}