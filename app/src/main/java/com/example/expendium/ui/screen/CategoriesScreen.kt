// ui/screen/CategoriesScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.expendium.data.model.Category
import com.example.expendium.data.model.TransactionType
import com.example.expendium.ui.navigation.AppDestinations
import com.example.expendium.ui.viewmodel.CategoryViewModel

// Data class for category grouping
data class CategoryGroup(
    val type: TransactionType,
    val categories: List<Category>,
    val title: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    navController: NavController,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf<Category?>(null) }
    var showSummary by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    // Process categories based on search and group by type
    val processedCategories = remember(categories, searchQuery) {
        val filteredCategories = if (searchQuery.isNotEmpty()) {
            categories.filter { category ->
                category.name.contains(searchQuery, ignoreCase = true)
            }
        } else {
            categories
        }

        val incomeCategories = filteredCategories.filter { it.type == TransactionType.INCOME }
        val expenseCategories = filteredCategories.filter { it.type == TransactionType.EXPENSE }

        listOfNotNull(
            if (expenseCategories.isNotEmpty()) CategoryGroup(
                TransactionType.EXPENSE,
                expenseCategories.sortedBy { it.name },
                "Expense Categories"
            ) else null,
            if (incomeCategories.isNotEmpty()) CategoryGroup(
                TransactionType.INCOME,
                incomeCategories.sortedBy { it.name },
                "Income Categories"
            ) else null
        )
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Categories",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    // Summary toggle button
                    IconButton(
                        onClick = { showSummary = !showSummary }
                    ) {
                        Icon(
                            imageVector = if (showSummary) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showSummary) "Hide Summary" else "Show Summary",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    navController.navigate(AppDestinations.ADD_EDIT_CATEGORY_ROUTE)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add Category",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "New Category",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Section
            item {
                AnimatedVisibility(
                    visible = showSummary && categories.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CategorySummaryCard(categories)
                }
            }

            // Search Section
            item {
                CategorySearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Content Section
            if (uiState.isLoading && categories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (processedCategories.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    CategoryEmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "No categories found",
                        subtitle = "Try adjusting your search terms"
                    )
                }
            } else if (categories.isEmpty()) {
                item {
                    CategoryEmptyState(
                        icon = Icons.Outlined.Category,
                        title = "No categories yet",
                        subtitle = "Start by adding your first category"
                    )
                }
            } else {
                // Category groups
                items(processedCategories) { group ->
                    CategoryTypeGroup(
                        group = group,
                        onEditClick = { category ->
                            navController.navigate("${AppDestinations.ADD_EDIT_CATEGORY_ROUTE}?categoryId=${category.categoryId}")
                        },
                        onDeleteClick = { category ->
                            showDeleteDialog = category
                        }
                    )
                }
            }

            // Bottom spacing for FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { categoryToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = {
                Text(
                    "Delete Category",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete '${categoryToDelete.name}'? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        viewModel.deleteCategory(categoryToDelete.categoryId)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun CategoryTypeGroup(
    group: CategoryGroup,
    onEditClick: (Category) -> Unit,
    onDeleteClick: (Category) -> Unit
) {
    Column {
        // Group Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (group.type == TransactionType.INCOME)
                    Color(0xFF00C853).copy(alpha = 0.1f)
                else
                    Color(0xFFFF1744).copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (group.type == TransactionType.INCOME)
                            Icons.AutoMirrored.Filled.TrendingUp
                        else
                            Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = if (group.type == TransactionType.INCOME)
                            Color(0xFF00C853)
                        else
                            Color(0xFFFF1744),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (group.type == TransactionType.INCOME)
                        Color(0xFF00C853).copy(alpha = 0.2f)
                    else
                        Color(0xFFFF1744).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${group.categories.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (group.type == TransactionType.INCOME)
                            Color(0xFF00C853)
                        else
                            Color(0xFFFF1744),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Categories for this type
        group.categories.forEach { category ->
            ModernCategoryItem(
                category = category,
                onEditClick = onEditClick,
                onDeleteClick = onDeleteClick
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun CategorySummaryCard(categories: List<Category>) {
    val incomeCategories = categories.filter { it.type == TransactionType.INCOME }
    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = "Categories Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CategorySummaryMetric(
                    label = "Income",
                    count = incomeCategories.size,
                    color = Color(0xFF00C853),
                    icon = Icons.AutoMirrored.Filled.TrendingUp
                )
                CategorySummaryMetric(
                    label = "Expense",
                    count = expenseCategories.size,
                    color = Color(0xFFFF1744),
                    icon = Icons.AutoMirrored.Filled.TrendingDown
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${categories.size}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CategorySummaryMetric(
    label: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = { Text("Search categories") },
        placeholder = { Text("Category name...") },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun ModernCategoryItem(
    category: Category,
    onEditClick: (Category) -> Unit,
    onDeleteClick: (Category) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick(category) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Type Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (category.type == TransactionType.INCOME)
                            Color(0xFF00C853).copy(alpha = 0.1f)
                        else
                            Color(0xFFFF1744).copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (category.type == TransactionType.INCOME)
                        Icons.AutoMirrored.Filled.TrendingUp
                    else
                        Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = if (category.type == TransactionType.INCOME)
                        Color(0xFF00C853)
                    else
                        Color(0xFFFF1744),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Category Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (category.type == TransactionType.INCOME) "Income Category" else "Expense Category",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (category.type == TransactionType.INCOME)
                        Color(0xFF00C853)
                    else
                        Color(0xFFFF1744)
                )
            }

            // Action Buttons
            Row {
                IconButton(
                    onClick = { onEditClick(category) }
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit Category",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { onDeleteClick(category) }
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete Category",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}