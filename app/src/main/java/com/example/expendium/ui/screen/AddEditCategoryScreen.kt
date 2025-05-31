// ui/screen/AddEditCategoryScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var categoryName by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var isEditing by remember { mutableStateOf(false) }
    var currentCategory by remember { mutableStateOf<Category?>(null) }

    val screenTitle = if (isEditing) "Edit Category" else "Add Category"
    val snackbarHostState = remember { SnackbarHostState() }

    // Load category for editing or prepare for new
    LaunchedEffect(categoryId) {
        if (categoryId != null && categoryId != -1L) {
            isEditing = true
            val categoryToEdit = categories.firstOrNull { it.categoryId == categoryId }
            currentCategory = categoryToEdit
            categoryToEdit?.let {
                categoryName = it.name
                selectedType = it.type
            }
        } else {
            isEditing = false
            categoryName = ""
            selectedType = TransactionType.EXPENSE
            currentCategory = null
        }
    }

    // Handle error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        screenTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Type Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Category Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryTypeChip(
                            label = "Expense",
                            icon = Icons.AutoMirrored.Filled.TrendingDown,
                            isSelected = selectedType == TransactionType.EXPENSE,
                            color = Color(0xFFFF1744),
                            modifier = Modifier.weight(1f),
                            enabled = !isEditing
                        ) {
                            if (!isEditing) {
                                selectedType = TransactionType.EXPENSE
                            }
                        }

                        CategoryTypeChip(
                            label = "Income",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            isSelected = selectedType == TransactionType.INCOME,
                            color = Color(0xFF00C853),
                            modifier = Modifier.weight(1f),
                            enabled = !isEditing
                        ) {
                            if (!isEditing) {
                                selectedType = TransactionType.INCOME
                            }
                        }
                    }

                    if (isEditing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Type cannot be changed for existing categories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Category Preview Card
            AnimatedVisibility(
                visible = categoryName.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedType == TransactionType.INCOME)
                            Color(0xFF00C853).copy(alpha = 0.1f)
                        else
                            Color(0xFFFF1744).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selectedType == TransactionType.INCOME)
                                Icons.AutoMirrored.Filled.TrendingUp
                            else
                                Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (selectedType == TransactionType.INCOME)
                                Color(0xFF00C853)
                            else
                                Color(0xFFFF1744),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedType == TransactionType.INCOME)
                                Color(0xFF00C853)
                            else
                                Color(0xFFFF1744)
                        )
                    }
                }
            }

            // Form Fields Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Category Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Category Name Field
                    ModernTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = "Category Name",
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        isError = uiState.errorMessage?.contains("name", ignoreCase = true) == true
                    )

                    // Optional: Icon Picker Placeholder
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Icon (Optional)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Choose an icon for this category",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { /* TODO: Open icon picker */ }
                            ) {
                                Text("Select")
                            }
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    keyboardController?.hide()
                    if (categoryName.isBlank()) {
                        viewModel.setError("Category name cannot be empty.")
                        return@Button
                    }

                    if (isEditing && currentCategory != null) {
                        viewModel.updateCategory(
                            currentCategory!!.copy(
                                name = categoryName,
                                type = selectedType
                            )
                        )
                    } else {
                        viewModel.addCategory(
                            name = categoryName,
                            type = selectedType
                        )
                    }

                    // Navigate back on success (you might want to add a success state check)
                    if (uiState.errorMessage == null && !uiState.isLoading) {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEditing) "Update Category" else "Save Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun CategoryTypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.5f
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.5f
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    isError: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        readOnly = readOnly,
        isError = isError,
        minLines = minLines,
        maxLines = maxLines,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}