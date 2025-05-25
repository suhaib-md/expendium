// ui/screen/AddEditCategoryScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.TransactionType
import com.example.expendium.ui.viewmodel.CategoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCategoryScreen(
    navController: NavController,
    categoryId: Long?, // -1L for new, otherwise ID of category to edit
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var categoryName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) } // Default to Expense
    var isEditing by remember { mutableStateOf(false) }
    var currentCategory by remember { mutableStateOf<Category?>(null) }

    // For snackbar error messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    LaunchedEffect(categoryId) {
        if (categoryId != -1L) {
            isEditing = true
            // Fetch the category to edit
            // This could also be done by having a specific function in ViewModel to load a category for editing
            val categoryToEdit = viewModel.categories.value.firstOrNull { it.categoryId == categoryId }
            // A more robust way would be to have viewModel.getCategoryById(categoryId).collectAsState()
            // For now, we'll try to find it in the existing list or fetch if not found.
            if (categoryToEdit == null) {
                // Ideally, fetch from repository if not in the current list
                // For simplicity here, we'll assume it might be in the list eventually or handle error
                // viewModel.loadCategoryForEditing(categoryId) // hypothetical function
            }

            currentCategory = categoryToEdit // Could be null if not found yet
            categoryToEdit?.let {
                categoryName = it.name
                selectedType = it.type
            }
        } else {
            isEditing = false
            categoryName = ""
            selectedType = TransactionType.EXPENSE // Reset for new
            currentCategory = null
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Category" else "Add Category") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (categoryName.isBlank()) {
                            viewModel.setError("Category name cannot be empty.") // Use viewModel to set error
                            return@IconButton
                        }
                        if (isEditing && currentCategory != null) {
                            viewModel.updateCategory(
                                currentCategory!!.copy(
                                    name = categoryName,
                                    type = selectedType
                                    // iconName could be updated here too
                                )
                            )
                        } else {
                            viewModel.addCategory(
                                name = categoryName,
                                type = selectedType
                                // iconName can be added here
                            )
                        }
                        // Navigate back only if there's no error or after successful operation
                        // The LaunchedEffect for errorMessage will show the error.
                        // Consider navigating back based on a success flag in uiState if needed.
                        if (uiState.errorMessage == null && !uiState.isLoading) { // Basic check
                            navController.popBackStack()
                        }

                    }) {
                        Icon(Icons.Filled.Check, contentDescription = if (isEditing) "Save Changes" else "Save Category")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = { Text("Category Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.errorMessage?.contains("name", ignoreCase = true) == true // Basic error check
            )

            Text("Category Type:", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedType == TransactionType.EXPENSE,
                    onClick = { selectedType = TransactionType.EXPENSE },
                    label = { Text("Expense") },
                    enabled = !isEditing // Optionally disable type change when editing for simplicity
                    // Or handle implications of changing type for existing transactions
                )
                FilterChip(
                    selected = selectedType == TransactionType.INCOME,
                    onClick = { selectedType = TransactionType.INCOME },
                    label = { Text("Income") },
                    enabled = !isEditing
                )
            }

            // Placeholder for Icon Picker
            // Text("Icon (Optional):", style = MaterialTheme.typography.titleMedium)
            // Button(onClick = { /* TODO: Open icon picker */ }) { Text("Select Icon") }

            if (uiState.isLoading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}