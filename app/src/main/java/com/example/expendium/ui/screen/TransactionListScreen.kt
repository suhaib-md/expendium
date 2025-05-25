// ui/screen/TransactionListScreen.kt
package com.example.expendium.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings // Import Settings icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.expendium.data.model.Transaction
import com.example.expendium.data.model.TransactionType
// Remove this if AppDestinations is not directly used here
// import com.example.expendium.ui.navigation.AppDestinations
import com.example.expendium.ui.navigation.navigateToTransactionDetail
import com.example.expendium.ui.viewmodel.TransactionUiState
import com.example.expendium.ui.viewmodel.TransactionViewModel
import com.example.expendium.ui.viewmodel.TransactionWithCategory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.expendium.ui.navigation.AppDestinations
import java.util.*

// Helper function (place in a utility file e.g., utils/Formatters.kt)
fun formatCurrency(amount: Double, locale: Locale = Locale.getDefault()): String {
    val currencyFormat = NumberFormat.getCurrencyInstance(locale)
    return currencyFormat.format(amount)
}

fun formatDateShort(timestamp: Long, format: String = "MMM dd, yyyy"): String {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    return sdf.format(Date(timestamp))
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    navController: NavController, // Still needed for item clicks
    viewModel: TransactionViewModel, // Passed from MainScreen's NavHost
    onNavigateToSettings: () -> Unit // Callback to navigate to settings
) {
    val transactionsWithCategories by viewModel.transactionsWithCategoryNames.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    // This Scaffold is local to TransactionListScreen.
    // If MainScreen provides a global Scaffold and TopAppBar, this might be simpler or different.
    Scaffold(
        /*
        // Example: If TransactionListScreen is responsible for its own TopAppBar
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) { // Use the callback here
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        */
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(horizontal = 16.dp) // Add horizontal padding for content
        ) {
            if (transactionsWithCategories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TransactionSummaryInfo(transactionsWithCategories.map { it.transaction })
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.searchTransactions(it) },
                label = { Text("Search by merchant or notes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading && transactionsWithCategories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (transactionsWithCategories.isEmpty() && uiState.searchQuery.isNotEmpty()) {
                EmptyState(
                    messageLine1 = "No transactions found",
                    messageLine2 = "Try a different search term."
                )
            } else if (transactionsWithCategories.isEmpty()) {
                EmptyState(
                    messageLine1 = "No transactions yet.",
                    messageLine2 = "Tap the '+' button to add your first one!"
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        transactionsWithCategories,
                        key = { it.transaction.transactionId }) { transactionWithCategory ->
                        TransactionItem(
                            transaction = transactionWithCategory.transaction,
                            categoryName = transactionWithCategory.categoryName,
                            onItemClick = { transactionIdValue ->
                                navController.navigateToTransactionDetail(transactionIdValue)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionSummaryInfo(transactions: List<Transaction>) {
    val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val netFlow = totalIncome - totalExpenses

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        SummaryItem("Income", totalIncome, Color(0xFF4CAF50))
        SummaryItem("Expenses", totalExpenses, MaterialTheme.colorScheme.error)
        SummaryItem("Net Flow", netFlow, if (netFlow >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
    }
}

@Composable
fun SummaryItem(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = formatCurrency(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}


@Composable
fun TransactionItem(
    transaction: Transaction,
    categoryName: String?,
    onItemClick: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick(transaction.transactionId) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchantOrPayee,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = categoryName ?: "Uncategorized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDateShort(transaction.transactionDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatCurrency(transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.type == TransactionType.INCOME) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun EmptyState(messageLine1: String, messageLine2: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Receipt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = messageLine1,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = messageLine2,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}