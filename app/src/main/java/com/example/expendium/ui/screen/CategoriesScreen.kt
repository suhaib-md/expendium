// ui/screen/CategoriesScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.TransactionType
import com.example.expendium.ui.navigation.AppDestinations
import com.example.expendium.ui.viewmodel.CategoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    navController: NavController,
    viewModel: CategoryViewModel = hiltViewModel(),
    // scaffoldPaddingValues: PaddingValues // Receive padding from MainScreen's Scaffold if needed
) {
    val categories by viewModel.categories.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Category?>(null) }

    // Use LaunchedEffect to show snackbar for errors
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError() // Important to clear the error after showing
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate(AppDestinations.ADD_EDIT_CATEGORY_ROUTE) // Navigate to add new
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Category")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) } // Add SnackbarHost
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
        ) {
            if (uiState.isLoading && categories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (categories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No categories found. Tap '+' to add one.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories, key = { it.categoryId }) { category ->
                        CategoryItem(
                            category = category,
                            onEditClick = {
                                navController.navigate("${AppDestinations.ADD_EDIT_CATEGORY_ROUTE}?categoryId=${category.categoryId}")
                            },
                            onDeleteClick = {
                                showDeleteDialog = category
                            }
                        )
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { categoryToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Category") },
            text = { Text("Are you sure you want to delete '${categoryToDelete.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(categoryToDelete.categoryId)
                    showDeleteDialog = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CategoryItem(
    category: Category,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick), // Make the whole card clickable for editing
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Placeholder for Icon - will add later
                // Icon(painterResource(id = category.iconResId ?: R.drawable.ic_placeholder), contentDescription = null)
                // Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (category.type == TransactionType.INCOME) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = category.type.name,
                    tint = if (category.type == TransactionType.INCOME) Color(0xFF4CAF50) else Color(0xFFF44336) // Green for Income, Red for Expense
                )

            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Category")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Category", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}